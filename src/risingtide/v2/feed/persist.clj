(ns risingtide.v2.feed.persist
  (:require
   [clojure.data.json :as json]
   [risingtide
    [core :refer [now]]
    [shard :as shard]
    [redis :as redis]
    [config :as config]]
   [risingtide.v2
    [story :as story]
    [feed :as feed]])
  (:import [risingtide.v2.story TagLikedStory ListingLikedStory ListingActivatedStory ListingSoldStory ListingSharedStory ListingCommentedStory MultiActionStory MultiActorStory MultiActorMultiActionStory MultiListingStory]))

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

(def long-key (reduce (fn [hash [key val]] (assoc hash val key)) {} short-key))

(defn- translate-keys
  [hash translator]
  (reduce (fn [h [key val]] (let [s (translator key)] (if s (assoc h s val) h))) {} hash))

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str (translate-keys (assoc story :type (story/type-sym story))
                                 short-key)))

(defn convert [story value-converter & keys]
  (reduce (fn [story key] (if (get story key)
                           (assoc story key (value-converter (get story key)))
                           story))
          story keys))

(defn convert-to-set-with-converter [story value-converter & keys]
  (apply convert story #(set (map value-converter %)) keys))

(defn convert-to-kw-set [story & keys]
  (apply convert-to-set-with-converter story keyword keys))

(defn convert-to-set [story & keys]
  (apply convert-to-set-with-converter story identity keys))

(defn keywordize [story & keys]
  (apply convert story keyword keys))

(defn decode
  "given a short-key json encoded story, decode into a long keyed hash"
  [string]
  (let [story (translate-keys (json/read-json string) long-key)]
    (-> ((story/story-factory-for (keyword (:type story))) story)
        (dissoc :type)
        (convert-to-kw-set :feed :actions)
        (convert-to-set :actor-ids :listing-ids)
        (keywordize :action))))

(defn- add-stories-to-jedis
  [jedis feed-key stories]
  (doseq [story stories]
    (.zadd jedis feed-key (double (story/score story)) (encode story))))

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
       (replace-feed-head! connection feed-key stories (feed/min-timestamp feed) (feed/max-timestamp feed))))))
