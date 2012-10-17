(ns risingtide.interests.brooklyn
  (:use korma.db
        korma.core)
  (:require [risingtide.config :as config]))

;; unmap dbs so they reload properly
(ns-unmap *ns* 'brooklyn-slave)

(defdb brooklyn-slave
  (mysql (config/brooklyn-slave)))

(defentity slave-follows
  (table :follows)
  (database brooklyn-slave))

(defentity slave-people
  (table :people)
  (database brooklyn-slave))

(defentity slave-users
  (table :users)
  (database brooklyn-slave))

(defn follow-to-actor-interest [follow]
  (str "a:" (:user_id follow)))

(defn user-follows [user-id]
  (map follow-to-actor-interest
       (select slave-follows
               (where {:follower_id user-id})
               (fields :user_id))))

;;; mutating methods - should only be used in test!!!
;;; currently working against the slave, so they won't even
;;; work on

(defn create-user [user-id]
  (insert slave-people (values {:id user-id}))
  (insert slave-users (values {:id user-id :person_id user-id})))

(defn create-follow [follower-id followee-id]
  (insert slave-follows (values {:user_id followee-id :follower_id follower-id})))

(defn clear-tables! []
  (delete slave-follows)
  (delete slave-users)
  (delete slave-people))
