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

(defentity collections
  (table :collections)
  (database brooklyn))

(defentity listing-collection-attachments
  (table :listing_collection_attachments)
  (database brooklyn))

(defentity collection-follows
  (table :collection_follows)
  (database brooklyn))

(defentity listing-follows-via-collections
  (table (subselect collection-follows
          (join listing-collection-attachments (= :collection_id :listing_collection_attachments.collection_id))
          (fields :user_id :listing_collection_attachments.listing_id))
         :listing_follows_via_collections)
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
    (select follows
            (fields [:follower_id :user_id] (raw "COUNT(*) AS cnt"))
            (where {:user_id followee-id
                    :follower_id [in follower-ids]})
            (group :follower_id))))


(defn collection-follow-counts [listing-id user-ids]
  (when (and listing-id user-ids)
    (select listing-follows-via-collections
            (fields :user_id (raw "COUNT(*) AS cnt"))
            (where {:listing_id listing-id
                    :user_id [in user-ids]})
            (group :user_id))))

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

(defn create-collection [collection-id owner-id]
  (insert collections (values {:id collection-id :user_id owner-id :name collection-id :slug collection-id})))

(defn create-listing-collection-attachment [collection-id listing-id]
  (insert listing-collection-attachments (values {:collection_id collection-id :listing_id listing-id})))

(defn create-collection-follow [follower-id collection-id]
  (insert collection-follows (values {:user_id follower-id :collection_id collection-id})))

(defn clear-tables! []
  (delete follows)
  (delete dislikes)
  (delete listing-collection-attachments)
  (delete collection-follows)
  (delete listings)
  (delete collections)
  (delete users)
  (delete people))

