(ns risingtide.feed.persist.shard.config
 "Sharding config server"
 (:require [risingtide
            [config :as config]
            [redis :as redis]
            [key :as key]
            [active-users :refer [active-user-key]]]
           [clojure.pprint :as pp]))

(def default-shard config/default-card-shard)

(def shard-value identity)

(def default-migrations-value {:card {}})

(def shard-hash-key "sc")

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
      (.hget jedis (active-user-key user-id) shard-hash-key))))

(defn get-or-create-shard-key
  [conn-spec type user-id]
  (let [key (active-user-key user-id)]
   (redis/with-jedis* (:shard-config conn-spec)
     (fn [jedis]
       (or (.hget jedis key shard-hash-key)
           (do
             (when (.exists jedis key)
               (.hset jedis key shard-hash-key default-shard))
             default-shard))))))

(defn update-shard-key
  [conn-spec type user-id new-key]
  (redis/with-jedis* (:shard-config conn-spec)
    (fn [jedis] (.hset jedis (active-user-key user-id) shard-hash-key new-key))))

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

