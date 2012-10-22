(ns risingtide.interests.brooklyn
  (:use korma.db
        korma.core)
  (:require [risingtide.config :as config]))

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

(defn follow-to-actor-interest [follow]
  (str "a:" (:user_id follow)))

(defn user-follows [user-id]
  (map follow-to-actor-interest
       (select follows
               (where {:follower_id user-id})
               (fields :user_id))))

(defn following? [follower-id followee-id]
  (> (:cnt (first (select follows (fields (raw "count(*) cnt"))
                          (where {:user_id followee-id
                                  :follower_id follower-id}))))
     0))

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
