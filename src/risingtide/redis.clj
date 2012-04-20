(ns risingtide.redis
  (:use risingtide.core)
  (:require [risingtide.config :as config])
  (:import [redis.clients.jedis JedisPool JedisPoolConfig]))

(defn redis [config]
  (JedisPool. (JedisPoolConfig.) (or (:host config) "localhost") (or (:port config) 6379) (or (:timeout config) 60000)))

(defn redii [env]
  (reduce (fn [m [k v]] (assoc m k (redis v))) {} (config/redis env)))

(defn with-jedis* [pool f]
  (let [jedis (.getResource pool)]
    (try
      (f jedis)
      (finally (.returnResource pool jedis)))))

(defn with-transaction* [pool f]
  (with-jedis* pool
    (fn [jedis]
      (let [transaction (.multi jedis)]
        (let [r (f transaction)]
          (.exec transaction)
          (.get r))))))
