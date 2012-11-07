(ns risingtide.feed.persist
  (:require
   [clojure.data.json :as json]
   [clojure.set :refer [map-invert rename-keys]]
   [risingtide
    [core :refer [now]]
    [redis :as redis]
    [config :as config]
    [key :as key]
    [persist :refer [keywordize convert-to-kw-set convert-to-set convert-to-kw-seq convert]]]
   [risingtide.model
    [story :as story]
    [feed :as feed]
    [timestamps :refer [timestamp min-timestamp max-timestamp with-timestamp]]]
   [risingtide.feed.persist.shard :as shard]))

(defn- max-feed-size
  []
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
   :network :n
   :timestamp :time
   :count :c})

(def long-key (map-invert short-key))

;;; encoding stories for redis

(defn- assoc-if [hash key val bool]
  (if bool
    (assoc hash key val)
    hash))

(defn encoded-hash [story & {include-ts :include-ts :or {include-ts false}}]
  (-> story
      (assoc :type (story/type-sym story))
      (assoc-if :timestamp (timestamp story) include-ts)
      (rename-keys short-key)))

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str (encoded-hash story)))

(defn encode-feed
  [feed & args]
  (json/json-str (map #(apply encoded-hash % args) (seq feed))))

(defn decode-actions [actions]
  (if (map? actions)
    (into {} (map (fn [[k v]] [k (set v)]) actions))
    (set (map keyword actions))))

(defn decode
  "given a short-key json encoded story, decode into a long keyed hash"
  [string]
  (let [story (rename-keys (json/read-json string) long-key)]
    (-> ((story/story-factory-for (keyword (:type story))) story)
        (dissoc :type)
        (convert-to-kw-seq :feed)
        (convert-to-set :actor-ids :listing-ids)
        (convert decode-actions :actions)
        (keywordize :action :network))))

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

;;; reading feeds from redis

(defn- parse-stories-and-scores
  [stories-and-scores]
  (for [tuple stories-and-scores]
    (with-timestamp (decode (.getElement tuple)) (.getScore tuple))))

(defn stories
  "Load stories from a feed set"
  ([conn key since until]
     (parse-stories-and-scores
      (redis/with-jedis* conn
        (fn [jedis] (.zrangeByScoreWithScores jedis key (double since) (double until))))))
  ([conn key] (stories conn key 0 (now))))

(defn feed
  [conn-spec feed-key since until]
  (try
    (shard/with-connection-for-feed conn-spec feed-key
      [connection] (stories connection feed-key since until))
    (catch Throwable e
      (throw (Throwable. (str "exception loading "feed-key) e)))))

(defn delete-feeds! [redii user-ids]
  (when (not (empty? user-ids))
   (shard/with-connections-for-feeds redii (map key/user-feed user-ids)
     [pool feed-keys]
     (redis/with-jedis* pool
       (fn [redis]
         (.del redis (into-array String feed-keys)))))))



