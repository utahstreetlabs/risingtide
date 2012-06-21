(ns risingtide.shard.config
 "Sharding config server"
 (:require [risingtide
            [config :as config]
            [redis :as redis]
            [key :as key]]
           [clojure.pprint :as pp]))

(def buckets {:card (key/format-key "card-feed-shard-config")})

(def default-shard config/default-card-shard)

(def shard-value identity)

(def default-migrations-value {:card {}})

(def migrations (atom default-migrations-value))

(defn clear-migrations!
  []
  (swap! migrations (constantly default-migrations-value)))

(defn add-migration!
  [type id destination-shard]
  (swap! migrations #(assoc-in % [type id] destination-shard)))

(defn remove-migration!
  [type id]
  (swap! migrations #(update-in % [type] dissoc id)))

(defn get-shard-key
  [conn-spec type user-id]
  (redis/with-jedis* (:shard-config conn-spec)
    (fn [jedis]
      (.hget jedis (buckets type) (str user-id)))))

(defn get-or-create-shard-key
  [conn-spec type user-id]
  (let [bucket (buckets type)]
    (redis/with-jedis* (:shard-config conn-spec)
      (fn [jedis]
        (or (.hget jedis bucket (str user-id))
            (do (.hset jedis bucket (str user-id) default-shard)
                default-shard))))))

(defn update-shard-key
  [conn-spec type user-id new-key]
  (redis/with-jedis* (:shard-config conn-spec)
    (fn [jedis] (.hset jedis (str (buckets type)) (str user-id) new-key))))

(defn card-feed-shard-key
  "Given a connection and a user id, return the shard key for that user"
  [conn-spec user-id]
  (get-or-create-shard-key conn-spec :card user-id))

(defn card-feed-shard-keys
  "Given a connection and a user id, return the shard key for that user"
  [conn-spec user-id]
  (let [stored-key (card-feed-shard-key conn-spec user-id)
        new-shard-key (get (:card @migrations) user-id)]
    (if new-shard-key [stored-key new-shard-key] [stored-key])))

(comment
  (card-feed-key nil "50")
  (get-or-create-shard-key (:shard-config (redis/redii :development)) "card-feed-shard-config" "47")
  (update-shard-key (:shard-config (redis/redii :development)) "card-feed-shard-config" "47" "1")
  )