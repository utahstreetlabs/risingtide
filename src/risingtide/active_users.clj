(ns risingtide.active-users
  (:require [risingtide
             [redis :as redis]
             [key :as key]]))

(defn active-user-key [id]
  (key/format-key "act" id))

(defn active-user-key-pattern []
  (key/format-key "act" "*"))

(defn active-users [redii]
  (map #(Integer/parseInt (last (.split % ":")))
       (redis/with-jedis* (redii :active-users)
         (fn [jedis]
           (.keys jedis (active-user-key-pattern))))))

(defn add-active-user [redis ttl user-id]
  (let [key (active-user-key user-id)]
    (.hset redis key "manual" "true")
    (.expire redis key ttl)))

(defn add-active-users [redii ttl & user-ids]
  ;; set expiration
  (redis/with-jedis* (redii :active-users)
    (fn [redis]
      (doseq [user-id user-ids]
        (add-active-user redis ttl user-id)))))

(defn active? [redii user-id]
  (redis/with-jedis* (redii :active-users)
    (fn [redis]
      (.exists redis (active-user-key user-id)))))
