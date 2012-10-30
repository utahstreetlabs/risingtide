(ns risingtide.feed.persist
  (:require
   [clojure.data.json :as json]
   [clojure.set :refer [map-invert rename-keys]]
   [risingtide
    [core :refer [now]]
    [redis :as redis]
    [config :as config]
    [persist :refer [keywordize convert-to-kw-set convert-to-set]]]
   [risingtide.model
    [story :as story]
    [feed :as feed]
    [timestamps :refer [timestamp min-timestamp max-timestamp]]]
   [risingtide.feed.persist.shard :as shard]))

(defn- max-feed-size
  [feed]
  (- 0 config/max-card-feed-size 1))

(def short-key
  {:feed :f
   :action :a
   :type :t
   :actions :ts
   :actor-id :aid
   :actor-ids :aids
   :listing-id :lid
   :listing-ids :lids
   :tag-id :tid
   :tag-ids :tids
   :buyer-id :bid
   :text :tx
   :network :n})

(def long-key (map-invert short-key))

;;; encoding stories for redis

(defn encoded-hash [story]
  (rename-keys (assoc story :type (story/type-sym story))
               short-key))

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str (encoded-hash)))

(defn encode-feed
  [feed]
  (json/json-str (map encoded-hash (seq feed))))

(defn decode
  "given a short-key json encoded story, decode into a long keyed hash"
  [string]
  (let [story (rename-keys (json/read-json string) long-key)]
    (-> ((story/story-factory-for (keyword (:type story))) story)
        (dissoc :type)
        (convert-to-kw-set :feed :actions)
        (convert-to-set :actor-ids :listing-ids)
        (keywordize :action))))

;;; writing feeds to redis

(defn- add-stories-to-jedis
  [jedis feed-key stories]
  (doseq [story stories]
    (.zadd jedis feed-key (double (timestamp story)) (encode story))))

(defn replace-feed-head!
  [conn feed-key stories low-score high-score]
  (redis/with-transaction* conn
    (fn [jedis]
      (.zremrangeByScore jedis feed-key (double low-score) (double high-score))
      (add-stories-to-jedis jedis feed-key stories)
      (.zremrangeByRank jedis feed-key 0 (max-feed-size)))))

(defn write-feed!
  [redii feed-key feed]
  (let [stories (seq feed)]
   (when (not (empty? stories))
     (shard/with-connection-for-feed redii feed-key
       [connection]
       (replace-feed-head! connection feed-key stories (min-timestamp feed) (max-timestamp feed))))))
