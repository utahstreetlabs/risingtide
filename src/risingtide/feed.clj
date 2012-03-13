(ns risingtide.feed
  "utilities for building feeds"
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide.key :as key]))

(defn user-feed-keys
  "get all user feed keys"
  [conn]
  (rexec conn
    (redis/keys (key/feed "u:*"))))

(defn card-story-keys
  "get all card story keys"
  [conn]
  (rexec conn
    (redis/keys (key/card-story "*"))))

(defn network-story-keys
  "get all network story keys"
  [conn]
  (rexec conn
    (redis/keys (key/network-story "*"))))

(defn interest-keys
  "get all interest keys"
  [conn]
  (rexec conn
    (redis/keys (key/interest "*"))))

(defn feed-source-interest-keys
  "given a feed type and a user id, get the keys of sets that will serve
as sources for that feed

currently, returns the actor interest key for network feeds and actor, listing and tag
interest keys for card feeds"
  [feed-type user-id]
  (map #(key/interest user-id %)
       (cons "a" (when (= :card feed-type) ["l" "t"]))))

(defn feed-type-prefix [feed-type]
  "given a feed type keywork return the prefix to use in key names"
  (first (name feed-type)))

(defn interesting-keys
  "return the keys of sets that should be included in the a user's feed of the given type"
  [conn feed-type user-id]
  (let [feed-type-key (feed-type-prefix feed-type)]
    (map #(key/format feed-type-key %)
         (rexec conn
           (apply redis/sunion (feed-source-interest-keys feed-type user-id))))))

(defn feed
  "build and store a feed of the given type for a user"
  [conn feed-type user-id]
  (let [format-key (key/feed (feed-type-prefix feed-type) user-id)]
    (rexec conn
      (zunionstore format-key (interesting-keys conn feed-type user-id) "AGGREGATE" "MIN"))))

(comment
  (def c (redis/connection-map {}))

  (interesting-keys c :notification "337")
  (feed c :notification 337)
  (rexec c (redis/zrevrange (feed-key :d "n" 337) 0 100))

)