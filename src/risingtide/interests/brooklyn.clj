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

(defentity dislikes
  (table :dislikes)
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

(defn user-follows [user-id lim]
  (select follows
          (where {:follower_id user-id})
          (fields :user_id)
          (limit lim)))

(defn following? [follower-id followee-id]
  (> (:cnt (first (select follows (fields (raw "COUNT(*) AS cnt"))
                          (where {:user_id followee-id
                                  :follower_id follower-id}))))
     0))

(defn follow-counts [followee-id follower-ids]
  (when (and followee-id follower-ids)
   (select follows (fields [:follower_id :user_id] (raw "COUNT(*) AS cnt"))
           (where {:user_id followee-id
                   :follower_id [in follower-ids]})
           (group :follower_id))))

(defn user-dislikes [user-id]
  (select dislikes
          (where {:user_id user-id})
          (fields :listing_id)))

(defn dislike-counts [listing-id user-ids]
  (when (and listing-id user-ids)
    (select dislikes (fields :user_id (raw "COUNT(*) AS cnt"))
            (where {:listing_id listing-id
                    :user_id [in user-ids]})
            (group :user_id))))

(defn find-listing [listing-id]
  (when listing-id
   (first (select listings
                  (where {:id listing-id})))))

(defn listings-for-sale [seller-ids lim]
  (select listings
          (where {:seller_id [in seller-ids]})
          (limit lim)))

;;; mutating methods - should only be used in test!!!

(defn create-user [user-id]
  (insert people (values {:id user-id}))
  (insert users (values {:id user-id :person_id user-id})))

(defn create-listing [listing-id seller-id]
  (insert listings (values {:id listing-id :seller_id seller-id :slug (str listing-id) :pricing_version 0})))

(defn create-follow [follower-id followee-id]
  (insert follows (values {:user_id followee-id :follower_id follower-id})))

(defn create-dislike [disliker-id listing-id]
  (insert dislikes (values {:user_id disliker-id :listing_id listing-id})))
(select dislikes)

(defn clear-tables! []
  (delete follows)
  (delete dislikes)
  (delete listings)
  (delete users)
  (delete people))

