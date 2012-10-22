(ns risingtide.interests.pyramid
  (:use korma.db
        korma.core)
  (:require [risingtide.config :as config]))

(ns-unmap *ns* 'pyramid)

(defdb pyramid
  (mysql (config/pyramid)))

(defentity likes
  (table :likes)
  (database pyramid))

(defn like-to-interest [like]
  (if (:listing_id like)
    (str "l:" (:listing_id like))
    (str "t:" (:tag_id like))))

(defn user-likes [user-id]
  (map like-to-interest
       (select likes
               (where {:user_id user-id})
               (fields :listing_id :tag_id))))

(defn likes? [user-id listing-id]
  (> (:cnt (first (select likes (fields (raw "count(*) cnt"))  (where {:user_id 47 :listing_id listing-id}))))
     0))

;;; mutating methods - should only be used in test!!!

(defn create-like [liker-id type object-id]
  (insert likes (values {:user_id liker-id (keyword (str (name type) "_id")) object-id :created_at (java.util.Date.) :updated_at (java.util.Date.)})))

(defn clear-tables! []
  (delete likes))
