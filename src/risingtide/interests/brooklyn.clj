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

(defn user-follows [user-id]
  (select follows
          (where {:follower_id user-id})
          (fields :user_id)))

(defn following? [follower-id followee-id]
  (> (:cnt (first (select follows (fields (raw "count(*) cnt"))
                          (where {:user_id followee-id
                                  :follower_id follower-id}))))
     0))

(defn follow-counts [followee-id follower-ids]
  (dissoc
   (->> (select follows (fields :follower_id (raw "count(*) cnt"))
                (where {:user_id followee-id
                        :follower_id [in follower-ids]}))
        (map (fn [{cnt :cnt follower-id :follower_id}] [follower-id cnt]))
        (into {})
        (merge (reduce #(assoc %1 %2 0) {} follower-ids)))
   nil))

;;; mutating methods - should only be used in test!!!

(defn create-user [user-id]
  (insert people (values {:id user-id}))
  (insert users (values {:id user-id :person_id user-id})))

(defn create-follow [follower-id followee-id]
  (insert follows (values {:user_id followee-id :follower_id follower-id})))

(defn clear-tables! []
  (delete follows)
  (delete users)
  (delete people))
