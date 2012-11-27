(ns risingtide.interests.pyramid
  (:require [risingtide.config :as config]
            [korma
             [db :refer :all]
             [core :refer :all]]))

(ns-unmap *ns* 'pyramid)

(defdb pyramid
  (mysql (config/pyramid)))

(defentity likes
  (table :likes)
  (database pyramid))

(defn user-likes [user-id lim]
  (select likes
          (where {:user_id user-id})
          (fields :listing_id :tag_id)
          (limit lim)))

(defn likes? [user-id listing-id]
  (> (:cnt (first (select likes (fields :user_id (raw "COUNT(*) AS cnt"))  (where {:user_id user-id :listing_id listing-id}))))
     0))

(defn like-counts [listing-id user-ids]
  (when (and listing-id user-ids)
    (select likes
            (fields :user_id (raw "COUNT(*) AS cnt"))
            (where {:user_id [in user-ids] :listing_id listing-id})
            (group :user_id))))

(defn tag-like-counts [tag-ids user-ids]
  (when (and tag-ids user-ids)
    (select likes
            (fields :user_id (raw "COUNT(*) AS cnt"))
            (where {:user_id [in user-ids] :tag_id [in tag-ids]})
            (group :user_id))))

;;; mutating methods - should only be used in test!!!

(defn create-like [liker-id type object-id]
  (insert likes (values {:user_id liker-id (keyword (str (name type) "_id")) object-id :created_at (java.util.Date.) :updated_at (java.util.Date.)})))

(defn clear-tables! []
  (delete likes))
