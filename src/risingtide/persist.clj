(ns risingtide.persist
  "Persistence layer for feeds and stories

Public api methods take either a connection spec map that looks like

 {:stories <connection-object>
  :card-feeds <connection-object>
  ...}

or a \"connection object\" directly. The connection object will be
redis-client specific, but in the current implementation should be a JedisPool
object.
"
  (:use risingtide.core)
  (:require
   [clojure.data.json :as json]
   [risingtide
    [config :as config]
    [redis :as redis]
    [feed :as feed]
    [shard :as shard]]))

;; truncation
;;
;; to save space in redis, we truncate feeds and story buckets to a fixed length

(defn- max-feed-size
  [feed]
  (case (last feed)
    \c (- 0 config/max-card-feed-size 1)))

;; key compression
;;
;; to save space in redis, we use terse keynames when encoding stories

(def short-key
  {:feed :f
   :action :a
   :type :t
   :types :ts
   :actor_id :aid
   :actor_ids :aids
   :listing_id :lid
   :listing_ids :lids
   :tag_id :tid
   :buyer_id :bid
   :followee_id :fid
   :invitee_profile_id :iid
   :text :tx
   :network :n
   :count :c})

(def long-key (reduce (fn [hash [key val]] (assoc hash val key)) {} short-key))

(defn- translate-keys
  [hash translator]
  (reduce (fn [h [key val]] (let [s (translator key)] (if s (assoc h s val) h))) {} hash))

(defn- encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str (translate-keys story short-key)))

(defn- decode
  "given a short-key json encoded story, decode into a long keyed hash"
  [story]
  (translate-keys (json/read-json story) long-key))

(defn- parse-stories-and-scores
  [stories-and-scores]
  (for [tuple stories-and-scores]
    (assoc (decode (.getElement tuple)) :score (.getScore tuple))))

;;;; public api ;;;;

(defn stories
  "Load stories from a story or feed set"
  ([conn key since until]
     (parse-stories-and-scores
      (redis/with-jedis* conn
        (fn [jedis] (.zrangeByScoreWithScores jedis key (double since) (double until))))))
  ([conn key] (stories conn key 0 (now))))

(defn- add-stories-to-jedis
  [jedis feed-key stories]
  (doseq [story stories]
    (.zadd jedis feed-key (double (:score story)) (encode story))))

(defn replace-feed-head!
  [conn feed-key stories low-score high-score]
  (when-not (empty? stories)
    (redis/with-transaction* conn
      (fn [jedis]
        (.zremrangeByScore jedis feed-key (double low-score) (double high-score))
        (add-stories-to-jedis jedis feed-key stories)
        (.zremrangeByRank jedis feed-key 0 (max-feed-size feed-key))))))

(defn add-stories!
  [conn feed-key stories]
  (redis/with-jedis* conn
    (fn [jedis] (add-stories-to-jedis jedis feed-key stories))))

(defn delete!
  [conn feed-key]
  (redis/with-jedis* conn
      (fn [jedis] (.del jedis (into-array String [feed-key])))))

(defn union-story-sets
  "Load stories from N story sets"
  [conn-spec keys limit]
  (parse-stories-and-scores
   (redis/zunion-withscores (:stories conn-spec) keys limit)))

(defn feed
  [conn-spec feed-key since until]
  (try
    (shard/with-connection-for-feed conn-spec feed-key
      [connection] (stories connection feed-key since until))
   (catch Throwable e
     (throw (Throwable. (str "exception loading" feed-key) e)))))

(defn add-story!
  [conn-spec story destination-sets time]
  (redis/with-jedis* (:stories conn-spec)
    (fn [jedis]
      (let [encoded-story (encode story)]
        (doseq [key destination-sets]
          (.zadd jedis key (double time) encoded-story))))))

(defn truncate-story-buckets
  [conn-spec story-bucket-keys]
  (redis/with-jedis* (:stories conn-spec)
    (fn [jedis]
      (doseq [key story-bucket-keys]
        (.zremrangeByRank jedis key 0 (- 0 config/max-story-bucket-size 1))))))
