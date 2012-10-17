(ns risingtide.interests.pyramid
  (:use korma.db
        korma.core)
  (:require [risingtide.config :as config]))

(ns-unmap *ns* 'pyramid-slave)

(defdb pyramid-slave
  (mysql (config/pyramid-slave)))

(defentity slave-likes
  (table :likes)
  (database pyramid-slave))

(defn like-to-interest [like]
  (if (:listing_id like)
    (str "l:" (:listing_id like))
    (str "t:" (:tag_id like))))

(defn user-likes [user-id]
  (map like-to-interest
       (select slave-likes
               (where {:user_id user-id})
               (fields :listing_id :tag_id))))


;;; mutating methods - should only be used in test!!!

(defn create-like [liker-id type object-id]
  (insert slave-likes (values {:user_id liker-id (keyword (str (name type) "_id")) object-id :created_at (java.util.Date.) :updated_at (java.util.Date.)})))

(defn clear-tables! []
  (delete slave-likes))
