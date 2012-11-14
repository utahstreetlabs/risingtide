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
  (dissoc
   (->> (select likes (fields :user_id (raw "COUNT(*) AS cnt")) (where {:user_id [in user-ids] :listing_id listing-id}))
        (map (fn [{cnt :cnt user-id :user_id}] [user-id cnt]))
        (into {})
        (merge (reduce #(assoc %1 %2 0) {} user-ids)))
   nil))

;;; mutating methods - should only be used in test!!!

(defn create-like [liker-id type object-id]
  (insert likes (values {:user_id liker-id (keyword (str (name type) "_id")) object-id :created_at (java.util.Date.) :updated_at (java.util.Date.)})))

(defn clear-tables! []
  (delete likes))
