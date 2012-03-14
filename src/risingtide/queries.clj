(ns risingtide.queries
  "queries that can be used inside redis/with-connection"
  (:use risingtide.core)
  (:require [accession.core :as redis]
            [risingtide.key :as key]))

(defn user-feed-keys
  "get all user feed keys"
  []
  (redis/keys (key/user-feed "*" "*")))

(defn card-story-keys
  "get all card story keys"
  []
  (redis/keys (key/card-story "*")))

(defn network-story-keys
  "get all network story keys"
  []
  (redis/keys (key/network-story "*")))

(defn interest-keys
  "get all interest keys"
  []
  (redis/keys (key/interest "*")))

(comment
  (def c (redis/connection-map {}))
  (first
   (redis/with-connection c
      (redis/zrevrange
       (first (redis/with-connection c (user-feed-keys)))
       0 100)))

  (redis/with-connection (redis/connection-map {}) (user-feed-keys))
  (redis/with-connection (redis/connection-map {}) (redis/zrevrange "magd:f:u:47:n" 0 100))
  )