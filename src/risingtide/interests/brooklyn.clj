(ns risingtide.interests.brooklyn
  (:require [risingtide.config :as config]
            [korma
             [db :refer :all]
             [core :refer :all]]))

;; unmap dbs so they reload properly
(ns-unmap *ns* 'brooklyn)

(defdb brooklyn
  (mysql (config/brooklyn)))

(defentity follows
  (table :follows)
  (database brooklyn))

(defentity people
  (table :people)
  (database brooklyn))

(defentity users
  (table :users)
  (database brooklyn))

(defentity listings
  (table :listings)
  (database brooklyn))

(defn user-follows [user-id]
  (select follows
          (where {:follower_id user-id})
          (fields :user_id)))

(defn following? [follower-id followee-id]
  (> (:cnt (first (select follows (fields (raw "COUNT(*) AS cnt"))
                          (where {:user_id followee-id
                                  :follower_id follower-id}))))
     0))

(defn follow-counts [followee-id follower-ids]
  (dissoc
   (->> (select follows (fields :follower_id (raw "COUNT(*) AS cnt"))
                (where {:user_id followee-id
                        :follower_id [in follower-ids]}))
        (map (fn [{cnt :cnt follower-id :follower_id}] [follower-id cnt]))
        (into {})
        (merge (reduce #(assoc %1 %2 0) {} follower-ids)))
   nil))

(defn find-listing [listing-id]
  (first (select listings
                 (where {:id listing-id}))))

(defn listings-for-sale [seller-ids]
  (select listings
          (where {:seller_id [in seller-ids]})))

;;; mutating methods - should only be used in test!!!

(defn create-user [user-id]
  (insert people (values {:id user-id}))
  (insert users (values {:id user-id :person_id user-id})))

(defn create-listing [listing-id seller-id]
  (insert listings (values {:id listing-id :seller_id seller-id :slug (str listing-id) :pricing_version 0})))

(defn create-follow [follower-id followee-id]
  (insert follows (values {:user_id followee-id :follower_id follower-id})))

(defn clear-tables! []
  (delete follows)
  (delete listings)
  (delete users)
  (delete people))

