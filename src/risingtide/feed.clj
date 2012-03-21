(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.key :as key]
            [risingtide.digest :as digest]
            [risingtide.digesting-cache :as dc]
            [risingtide.stories :as stories]))

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

(defn interesting-keys
  "return the keys of sets that should be included in the a user's feed of the given type"
  [conn feed-type user-id]
  (let [f (feed-type-key feed-type)]
    (map #(key/format-key f %)
         (redis/with-connection conn
           (interesting-key-query feed-type user-id)))))

(defn build-feed
  "returns a query that will build and store a feed of the given type for a user"
  [feed-type user-id interesting-story-keys]
  (let [feed-key (key/user-feed user-id (feed-type-key feed-type))]
    (log/info "Generating feed" feed-key)
    (redis/zunionstore feed-key interesting-story-keys "AGGREGATE" "MIN")))

(defn interesting-keys-for-feeds
  [conn feeds]
  (map #(apply interesting-keys conn (key/feed-type-user-id-from-key %)) feeds))

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
    [(redis/multi)
     (redis/zremrangebyscore feed low-score high-score)
     (apply redis/zadd feed (scored-encoded-stories stories))
     (redis/exec)]))

(defn redigest-user-feeds
  [conn destination-feeds]
  ;; don't feel awesome about how I'm getting high/low scores to
  ;; pass to zremrangebyscore - should perhaps actually look through
  ;; digested stories for high/low scores?
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)
        digested-stories (map digest/digest
                              (map #(dc/stories-for-interests cache %)
                                   (interesting-keys-for-feeds conn destination-feeds)))]
    (flatten
     (map replace-feed-head destination-feeds digested-stories (repeat low-score) (repeat high-score)))))

(defn redigest-everything-feed
  []
  (let [cache @dc/story-cache
        low-score (:low-score cache)
        high-score (:high-score cache)]
    (replace-feed-head (key/everything-feed) (digest/digest (dc/all-stories cache))
                       low-score high-score)))