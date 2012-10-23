(ns risingtide.test
  (:require
   [risingtide.core :refer :all]
   [risingtide.config]
   [clj-logging-config.log4j :as log-config]
   [risingtide.v2.story :refer [with-score score] :as story]
   [midje.sweet :refer :all]))

(log-config/set-logger! :level :debug)
(alter-var-root #'risingtide.config/env (constantly :test))


;; users
(defmacro defuser
  [n id]
  `(do
    (def ~n ~id)))

(defuser jim 1)
(defuser jon 2)
(defuser bcm 3)
(defuser dave 4)
(defuser rob 5)
(defuser cutter 6)
(defuser kaitlyn 7)
(defuser courtney 8)


;; profiles

(def mark-z :markz)

;; listings

(def bacon 100)
(def ham 101)
(def eggs 102)
(def muffins 103)
(def breakfast-tacos 104)
(def toast 105)
(def scones 106)
(def croissants 107)
(def danishes 108)
(def omelettes 109)
(def nail-polish 110)

;; tags

(def breakfast 200)

;; stories

(def jim-activated-ham (with-score (story/->ListingActivatedStory jim ham [] nil) 1))
(def jim-liked-ham (with-score (story/->ListingLikedStory jim ham [] nil) 2))
(def jim-shared-ham (with-score (story/->ListingSharedStory jim ham [] :facebook nil) 3))
(def jim-sold-ham (with-score (story/->ListingSoldStory jim ham [] cutter nil) 4))

(def cutter-liked-ham (with-score (story/->ListingLikedStory cutter ham [] nil) 5))
(def cutter-shared-ham (with-score (story/->ListingSharedStory cutter ham [] :facebook nil) 6))
(def jim-liked-bacon (with-score (story/->ListingLikedStory jim bacon [] nil) 7))
(def jim-liked-toast (with-score (story/->ListingLikedStory jim toast [] nil) 8))
(def rob-shared-ham (with-score (story/->ListingSharedStory rob ham [] :facebook nil) 9))
;; unlikely
(def jim-shared-muffins (with-score (story/->ListingSharedStory jim muffins [] :facebook nil) 10))

(defmacro expose
  "def a variable in the current namespace. This can be used to expose a private function."
  [& vars]
  `(do
     ~@(for [var vars]
         `(def ~(symbol (name var)) (var ~var)))))


(defn listing-story
  ([type actor-id listing-id args]
     (merge {:type type :actor_id actor-id :listing_id listing-id} args))
  ([type actor-id listing-id] (listing-story type actor-id listing-id nil)))

(defmacro listing-story-helper
  [name]
  `(defn ~name
     ([actor-id# listing-id# args#]
        (listing-story ~(.replace (str name) "-" "_") actor-id# listing-id# args#))
     ([actor-id# listing-id#] (~name actor-id# listing-id# {:score (now)}))))

(listing-story-helper listing-activated)
(listing-story-helper listing-liked)
(listing-story-helper listing-shared)
(listing-story-helper listing-sold)
(listing-story-helper listing-commented)

(defmacro expose
  "def a variable in the current namespace. This can be used to expose a private function."
  [& vars]
  `(do
     ~@(for [var vars]
         `(def ~(symbol (name var)) (var ~var)))))

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
