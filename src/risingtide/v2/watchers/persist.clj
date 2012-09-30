(ns risingtide.v2.watchers.persist
  (require [risingtide
            [config :as config]
            [redis :as redis]]))

(defn jedis [] (:interests (redis/redii config/env)))

(defn get-watchers [keys]
  ;; XXX: don't create new jedis every time
  (redis/with-jedis* (jedis)
    (fn [jedis] (.sunion jedis (into-array String keys)))))
