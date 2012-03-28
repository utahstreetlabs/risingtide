(ns risingtide.test
  (:use risingtide.core)
  (:require [clj-logging-config.log4j :as log-config]
            [risingtide.stories :as stories]))

(defn listing-story
  ([type actor-id listing-id score]
     (let [s {:type type :actor_id actor-id :listing_id listing-id :score score}]
       (assoc s :encoded (stories/encode s))))
  ([type actor-id listing-id] (listing-story type actor-id listing-id nil)))

(defmacro listing-story-helper
  [name]
  `(defn ~name
     ([actor-id# listing-id# score#]
        (listing-story ~(.replace (str name) "-" "_") actor-id# listing-id# score#))
     ([actor-id# listing-id#] (~name actor-id# listing-id# (now)))))

(listing-story-helper listing-activated)
(listing-story-helper listing-liked)
(listing-story-helper listing-shared)
(listing-story-helper listing-sold)
(listing-story-helper listing-commented)

(defn tag-liked
  ([actor-id tag-id score]
     {:type :tag_liked :actor_id actor-id :tag_id tag-id :score score})
  ([actor-id tag-id] (tag-liked actor-id tag-id (now))))

(defn user-joined
  ([actor-id score]
     {:type :user_joined :actor_id actor-id :score score})
  ([actor-id] (user-joined actor-id (now))))

(defn user-followed
  ([actor-id followee-id score]
     {:type :user_followed :actor_id actor-id :followee_id followee-id :score score})
  ([actor-id followee-id] (user-followed actor-id followee-id (now))))

(defn user-invited
  ([actor-id invitee-profile-id score]
     {:type :user_invited :actor_id actor-id :invitee_profile_id invitee-profile-id :score score})
  ([actor-id invitee-profile-id] (user-followed actor-id invitee-profile-id (now))))

(defn user-piled-on
  ([actor-id invitee-profile-id score]
     {:type :user_piled_on :actor_id actor-id :invitee_profile_id invitee-profile-id :score score})
  ([actor-id invitee-profile-id] (user-followed actor-id invitee-profile-id (now))))
