(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.config :as config]
            [risingtide.digest :as digest]
            [risingtide.digesting-cache :as dc]
            [risingtide.key :as key]
            [risingtide.stories :as stories]
            [risingtide.queries :as queries]
            [risingtide.interesting-story-cache :as interesting]))

;;;; keys ;;;;

(defn feed-type-key [feed-type]
  "given a feed type keyword return the single character name to use when constructing keys"
  (first-char feed-type))

(defn interesting-story-keys
  "return the story keys of sets that should be included in the a user's feed of the given type"
  [redii feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %) (interesting/feed-stories redii user-id feed-type))))

(defn interesting-keys-for-feeds
  [redii feeds]
  (map #(apply interesting-story-keys redii (key/type-user-id-from-feed-key %)) feeds))


;;;; filtering ;;;;

(def ev-feed-token "ev")
(def ev-feed-token? #{ev-feed-token})

(def user-feed-token "ylf")
(def user-feed-token? #{user-feed-token})

(def default-feeds [ev-feed-token user-feed-token])

(defn for-feed-with-token?
  [story token token-set]
  (let [f (get story :feed default-feeds)]
    (or (= f nil) (= f token) (some token-set f))))

(defn for-everything-feed?
  [story]
  (for-feed-with-token? story ev-feed-token ev-feed-token?))

(defn for-user-feed?
  [story]
  (for-feed-with-token? story user-feed-token user-feed-token?))

(defn everything-feed-stories
  [stories]
  (filter for-everything-feed? stories))

(defn user-feed-stories
  [stories]
  (filter for-user-feed? stories))


;;;; connection negotation ;;;;
;;
;; not all feeds live on the same redis. these utilities make it
;; easier to live in this world.

(defn feeds-by-type
  [feeds]
  (reduce
   (fn [m feed]
     (case (last feed)
       \c (assoc m :card-feeds (cons feed (get m :card-feeds)))
       \n (assoc m :network-feeds (cons feed (get m :network-feeds)))))
   {} feeds))

(defn map-across-connections-and-feeds
  "given a redii map, a collection of feeds and function of two arguments like

 (fn [connection feeds] (do-stuff))

call the function once for each set of feeds that live on different servers. The function
will be passed the connection spec for the server to use and the set of feeds that live
on the server specified by that connection spec.
"
  [redii feeds f]
  (map #(apply f %)
       (map (fn [[redis-key feeds]] [(redii redis-key) feeds]) (feeds-by-type feeds))))

(defmacro with-connections-for-feeds
  [redii feeds params & body]
  `(doall (map-across-connections-and-feeds ~redii ~feeds (fn ~params ~@body))))

;;;; feed building ;;;;

(defn zunion-withscores
  [redii story-keys limit & args]
  (nth
   (nth
    (redis/with-connection (:stories redii)
      (redis/multi)
      (apply redis/zunionstore "rtzuniontemp" story-keys args)
      (redis/zrange "rtzuniontemp" (- 0 limit) -1 "WITHSCORES")
      (redis/del "rtzuniontemp")
      (redis/exec))
    4) 1))

(defn- parse-stories-and-scores
  [stories-and-scores]
  (for [[story score] (partition 2 stories-and-scores)]
    (assoc (stories/decode story) :score (Long. score))))

(defn- fetch-filter-digest-user-stories
  [redii interesting-story-keys]
  (user-feed-stories
   (digest/digest
    (parse-stories-and-scores
     (zunion-withscores redii interesting-story-keys 1000 "AGGREGATE" "MIN")))))

(defn- zadd-encode-stories
  [stories]
  (flatten (map #(vector (:score %) (stories/encode %)) stories)))

(defn- build-user-feed-query
  "returns a query that will build and store a feed of the given type for a user"
  ([redii user-id feed-type interesting-story-keys]
     (let [feed-key (key/user-feed user-id feed-type)
           stories (zadd-encode-stories (fetch-filter-digest-user-stories redii interesting-story-keys))]
       (when (not (empty? stories))
         (apply redis/zadd feed-key stories))))
  ([redii feed-key]
     (let [[feed-type user-id] (key/type-user-id-from-feed-key feed-key)]
       (build-user-feed-query redii user-id feed-type (interesting-story-keys redii feed-type user-id)))))

(defn build!
  [redii feeds-to-build]
  (with-connections-for-feeds redii feeds-to-build [connection feeds]
    (let [queries (filter identity (map #(build-user-feed-query redii %) feeds))]
      (when (not (empty? queries))
        (apply redis/with-connection connection queries)))))

(defn build-for-user!
  [redii user-id]
  (build! redii [(key/user-card-feed user-id) (key/user-network-feed user-id)]))

;;;; redigesting ;;;;

(defn- scored-encoded-stories
  [stories]
  (interleave (map :score stories) (map :encoded stories)))

(defn- replace-feed-head-query
  [feed stories low-score high-score]
  (if (empty? stories)
    []
    [(redis/zremrangebyscore feed low-score high-score)
     (apply redis/zadd feed (scored-encoded-stories stories))]))

(defn- build-redigest-user-feeds-queries
  [redii destination-feeds]
  ;; don't feel awesome about how I'm getting high/low scores to
  ;; pass to zremrangebyscore - should perhaps actually look through
  ;; digested stories for high/low scores?
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)
        digested-stories (bench "digest" (doall (map digest/digest (map user-feed-stories
                           (bench "stories" (doall (map #(dc/stories-for-interests cache %)
                                                        (bench "interesting" (doall (interesting-keys-for-feeds redii destination-feeds))))))))))]
    (flatten
     (bench "replace" (doall (pmap replace-feed-head-query destination-feeds digested-stories (repeat low-score) (repeat high-score)))))))

(defn redigest-user-feeds!
  [redii destination-feeds]
  (bench (str "redigesting" (count destination-feeds) "user feeds")
         (with-connections-for-feeds redii destination-feeds [redis feeds]
           (apply redis/with-connection redis
                  (build-redigest-user-feeds-queries redii feeds)))))

(defn- build-redigest-everything-card-feed-queries
  []
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)]
    (replace-feed-head-query
     (key/everything-feed)
     (digest/digest (everything-feed-stories (dc/all-card-stories cache)))
     low-score high-score)))

(defn redigest-everything-feed!
  [redii]
  (bench "redigesting everything feed"
         (apply redis/with-connection (:card-feeds redii) (build-redigest-everything-card-feed-queries))))

(defn add!
  "add a story with the given score to the set of feeds that are interested in it"
  [redii story score]
  (let [encoded-story (stories/encode story)]
    (with-connections-for-feeds redii (stories/interested-feeds redii story) [connection feeds]
      (apply redis/with-connection connection
            (map #(redis/zadd % score encoded-story) feeds)))))