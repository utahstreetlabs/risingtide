(ns risingtide.shard.config
 "Sharding config server"
 (:require [risingtide.config :as config]
           [sumo.client :as riak]
           [clojure.pprint :as pp]))

(def default-shard config/default-card-shard)

(defn shard-value
  [value]
  {:value value :content-type "text/plain"})

(defn get-or-create-shard-key
  [client bucket user-id]
  (let [values (riak/get client bucket user-id)]
    (if (empty? values)
      (do (riak/put client bucket user-id (shard-value default-shard))
          default-shard)
      (:value (first values)))))

(defn update-shard-key
  [client bucket user-id new-key]
  (riak/put client bucket user-id (shard-value new-key)))

(defn card-feed-shard-key
  "Given a connection and a user id, return the shard key for that user"
  [client user-id]
  (get-or-create-shard-key client "card-feed-shard-config" user-id))

(comment
  (card-feed-key (riak/connect-pb) "50")
  (update-shard-key (riak/connect-pb) "card-feed-shard-config" "47" "3")
  )