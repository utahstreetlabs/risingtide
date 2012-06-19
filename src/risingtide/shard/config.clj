(ns risingtide.shard.config
 "Sharding config server"
 (:require [risingtide
            [config :as config]
            [redis :as redis]]
           [clojure.pprint :as pp]))

(def default-shard config/default-card-shard)

(def shard-value identity)

(defn get-or-create-shard-key
  [client bucket user-id]
  (redis/with-jedis* client
    (fn [jedis]
      (or (.hget jedis (str bucket) (str user-id))
          (do (.hset jedis (str bucket) (str user-id) default-shard)
              default-shard)))))

(defn update-shard-key
  [client bucket user-id new-key]
  (redis/with-jedis* client
    (fn [jedis] (.hset jedis (str bucket) (str user-id) new-key))))

(defn card-feed-shard-key
  "Given a connection and a user id, return the shard key for that user"
  [client user-id]
  (get-or-create-shard-key client "card-feed-shard-config" user-id))

(comment
  (card-feed-key nil "50")
  (get-or-create-shard-key (:shard-config (redis/redii :development)) "card-feed-shard-config" "47")
  (update-shard-key (:shard-config (redis/redii :development)) "card-feed-shard-config" "47" "1")
  )