(ns risingtide.queries
  "queries that can be used inside redis/with-connection"
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide.key :as key]))

(defn user-feed-keys
  "get all user feed keys"
  ([id type]
     (redis/keys (key/user-feed id type)))
  ([] (user-feed-keys "*" "*")))

(defn story-keys
  "get all network and card story keys"
  []
  (redis/keys (key/story-pattern "*")))

(defn card-story-keys
  "get all card story keys"
  []
  (redis/keys (key/card-story "*")))

(defn network-story-keys
  "get all network story keys"
  []
  (redis/keys (key/network-story "*")))

(defn watchers-keys
  "get all watcher set keys"
  []
  (redis/keys (key/watchers "*")))

(comment
  (redis/with-connection (redis/connection-map {}) (user-feed-keys))
  (redis/with-connection (redis/connection-map {})
   (redis/zscore "magd:f:u:47:n"
                  (first (redis/with-connection (redis/connection-map {}) (redis/zrevrange "magd:f:u:47:n" 0 100)))))
  )