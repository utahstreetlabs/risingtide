(ns risingtide.story.persist.browse-cache
  (:require
   [risingtide
    [config :as config]
    [redis :as redis]
    [key :refer [card-listing-story]]]
   [risingtide.feed
    [persist :refer [encode]]]
   [risingtide.model
    [timestamps :refer [timestamp]]]))

(defn truncate-story-set
  [conn-spec key]
  (redis/with-jedis* (:stories conn-spec)
    (fn [jedis]
      (.zremrangeByRank jedis key 0 -2))))

(defn add-story!
  [conn-spec story key]
  (redis/with-jedis* (:stories conn-spec)
    (fn [jedis]
      (.zadd jedis key (double (timestamp story)) (encode (dissoc story :timestamp))))))

(defn add!
  [conn-spec story]
  (let [key (card-listing-story (:listing-id story))]
    (add-story! conn-spec story key)
    (truncate-story-set conn-spec key)))
