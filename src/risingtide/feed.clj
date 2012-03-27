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
            [risingtide.interesting-story-cache :as isc]))

(def interests-for-feed-type
  {:card (map first-char [:actor :listing :tag])
   :network (map first-char [:actor])})

(defn- feed-source-interest-keys
  "given a feed type and a user id, get the keys of sets that will serve
as sources for that feed

currently, returns the actor interest key for network feeds and actor, listing and tag
interest keys for card feeds"
  [feed-type user-id]
  (map #(key/interest user-id %)
       (interests-for-feed-type feed-type)))

(defn feed-type-key [feed-type]
  "given a feed type keyword return the single character name to use when constructing keys"
  (first-char feed-type))

(defn interesting-key-query
  [feed-type user-id]
  (apply redis/sunion (feed-source-interest-keys feed-type user-id)))

(defn interesting-story-keys
  "return the story keys of sets that should be included in the a user's feed of the given type"
  [conn feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %)
         (or
          (isc/get-interesting-stories-for-feed @isc/interesting-story-cache feed-type user-id)
          (let [stories
                (redis/with-connection conn (interesting-key-query feed-type user-id))]
            (isc/cache-interesting-stories-for-feed! isc/interesting-story-cache stories feed-type user-id)
            stories)))))

(defn build-feed-query
  "returns a query that will build and store a feed of the given type for a user"
  [user-id feed-type interesting-story-keys]
  (let [feed-key (key/user-feed user-id feed-type)]
    (log/info "Generating feed" feed-key)
    (redis/zunionstore feed-key interesting-story-keys "AGGREGATE" "MIN")))

(defn build-feed
  [conn user-id feed-type]
  (redis/with-connection conn
    (build-feed-query user-id feed-type (interesting-story-keys conn feed-type user-id))))

(defn build-and-truncate-feed
  [conn user-id feed-type]
  [(build-feed-query user-id feed-type (interesting-story-keys conn feed-type user-id))
   (redis/zremrangebyrank (key/user-feed user-id feed-type) 0 -1001)])

(defn interesting-keys-for-feeds
  [conn feeds]
  (map #(apply interesting-story-keys conn (key/type-user-id-from-feed-key %)) feeds))

(defn scored-encoded-story
  [story]
  [(:score story) (stories/encode story)])

(defn scored-encoded-stories
  [stories]
  (flatten (map scored-encoded-story stories)))

(defn replace-feed-head
  [feed stories low-score high-score]
  (if (empty? stories)
    []
    [(redis/zremrangebyscore feed low-score high-score)
     (apply redis/zadd feed (scored-encoded-stories stories))]))

(defn redigest-user-feeds
  [conn destination-feeds]
  ;; don't feel awesome about how I'm getting high/low scores to
  ;; pass to zremrangebyscore - should perhaps actually look through
  ;; digested stories for high/low scores?
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)
        digested-stories (bench "digest" (doall (map (if (get config/digest (env) true) digest/digest identity)
                              (bench "stories" (doall (map #(dc/stories-for-interests cache %)
                                   (bench "interesting" (doall (interesting-keys-for-feeds conn destination-feeds)))))))))]
    (flatten
     (bench "replace" (doall (pmap replace-feed-head destination-feeds digested-stories (repeat low-score) (repeat high-score)))))))

(defn redigest-everything-feed
  []
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)]
    (replace-feed-head (key/everything-feed) (digest/digest (dc/all-card-stories cache))
                       low-score high-score)))

(defn- stories-and-scores
  [conn start-score end-score]
  (let [stories-and-scores-queries
        (map #(redis/zrangebyscore % start-score end-score "WITHSCORES")
             (redis/with-connection conn (queries/story-keys)))]
    (if (empty? stories-and-scores-queries)
      []
      (partition 2 (apply concat
                          (apply redis/with-connection conn stories-and-scores-queries))))))

(defn preload-digest-cache!
  [conn ttl]
  (doseq [[story score] (stories-and-scores conn (- (now) ttl) (now))]
    (dc/cache-story (stories/decode story) (Long. score))))

(comment
  (preload-digest-cache! (redis/connection-map) (* 24 60 60 5))
  (redis/with-connection
    (redis/connection-map) (queries/story-keys))
  (build-feed (redis/connection-map) 47 :card)
  (build-feed (redis/connection-map) 47 :network)

  (redis/with-connection (redis/connection-map)
    (redis/zrange (key/user-feed 47 :card) 0 100))

  (redis/with-connection (redis/connection-map)
    (queries/interest-keys 47 "a"))

  (redis/with-connection (redis/connection-map)
   (interesting-key-query :card 47))
  )

