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

(defn scored-encoded-stories
  [stories]
  (interleave (map :score stories) (map :encoded stories)))

(defn replace-feed-head
  [feed stories low-score high-score]
  (if (empty? stories)
    []
    [(redis/zremrangebyscore feed low-score high-score)
     (apply redis/zadd feed (scored-encoded-stories stories))]))


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
     (bench "replace" (doall (pmap replace-feed-head destination-feeds digested-stories (repeat low-score) (repeat high-score)))))))

(defn redigest-user-feeds!
  [redii destination-feeds]
  (bench (str "redigesting" (count destination-feeds) "user feeds")
         (apply redis/with-connection (:feeds redii)
                (build-redigest-user-feeds-queries redii destination-feeds))))

(defn- build-redigest-everything-feed-queries
  []
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)]
    (replace-feed-head (key/everything-feed) (digest/digest
                                              (everything-feed-stories
                                               (dc/all-card-stories cache)))
                       low-score high-score)))

(defn redigest-everything-feed!
  [redii]
  (bench "redigesting everything feed"
         (apply redis/with-connection (:feeds redii) (build-redigest-everything-feed-queries))))
