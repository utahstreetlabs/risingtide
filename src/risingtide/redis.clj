(ns risingtide.redis
  "Generic redis utilities"
  (:require [risingtide
             [config :as config]
             [core :refer :all]])
  (:import [redis.clients.jedis JedisPool JedisPoolConfig ZParams ZParams$Aggregate]
           java.util.Map))

(defn pool-config []
  (doto (JedisPoolConfig.)
    (.setMaxActive 30)))

(defn redis [config]
  (let [pool
        (JedisPool. (pool-config) (or (:host config) "localhost") (or (:port config) 6379) (or (:timeout config) 60000))]
    (if (:db config)
      (assoc config :pool pool)
      pool)))

(defn redii []
  (reduce (fn [m [k v]] (assoc m k (redis v))) {} (config/redis config/env)))

(defprotocol JedisConnectionPool
  (get-resource [pool])
  (return-resource [pool jedis]))

(extend-protocol JedisConnectionPool
  JedisPool
  (get-resource [pool] (.getResource pool))
  (return-resource [pool jedis] (.returnResource pool jedis))
  Map
  (get-resource [pool]
    (let [r (.getResource (:pool pool))]
      (.select r (or (:db pool) 0))
      r))
  (return-resource [pool jedis]
    (.returnResource (:pool pool) jedis)))

(defn with-jedis* [pool f]
  (let [jedis (get-resource pool)]
    (try
      (f jedis)
      (finally (return-resource pool jedis)))))

(defn with-transaction* [pool f]
  (with-jedis* pool
    (fn [jedis]
      (let [transaction (.multi jedis)]
        (let [r (f transaction)]
          (.exec transaction)
          (.get r))))))

(defn zunion-withscores
  [pool story-keys limit]
  (if (empty? story-keys)
    []
    (with-transaction* pool
      (fn [jedis]
        (let [tmp "rtzuniontmp"]
          (.zunionstore jedis tmp (.aggregate (ZParams.) ZParams$Aggregate/MIN) (into-array String story-keys))
          (let [r (.zrangeWithScores jedis tmp (- 0 limit) -1)]
            (.del jedis (into-array String [tmp]))
            r))))))
