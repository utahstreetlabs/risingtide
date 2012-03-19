(ns risingtide.stories
  "utilities for managing stories"
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide.key :as key]
            [risingtide.queries :as queries]))

(def type-info
  {:listing_activated { :group :listing :id 0 }
   :listing_liked { :group :listing :id 1 }
   :listing_shared { :group :listing :id 2 }
   :listing_sold { :group :listing :id 3 }
   :listing_commented { :group :listing :id 4 }
   :tag_liked { :group :tag :id 5 }
   :user_followed { :group :user :id 7 }
   :user_invited { :group :user :id 8 }
   :user_joined { :group :user :id 9 }
   :user_piled_on { :group :user :id 10 }})

(def group-key
  {:user :a :listing :l :tag :t})

;; group interest keys for each group type. right now, this looks like:
;; {:tag (:a :t), :user (:a), :listing (:a :l)}
(def group-interest-keys
  (reduce (fn [m [group-name this-group-key]]
            (assoc m group-name (distinct [(group-key :user) this-group-key])))
          {} group-key))

(def feed-type
  {:user :n :listing :c :tag :c})

(def id-key
  {:a :actor_id :l :listing_id :t :tag_id})

(def listing-tag-story-types #{:listing_activated :listing_sold})

(def short-key
  {:type :t
   :types :ts
   :actor_id :aid
   :actor_ids :aids
   :listing_id :lid
   :tag_id :tid
   :buyer_id :bid
   :followee_id :fid
   :invitee_profile_id :iid
   :text :tx
   :network :n})

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str
   (reduce (fn [h [key val]] (let [s (short-key key)] (if s (assoc h s val) h)))
           {} story)))

(defn group
  [story]
  (:group (type-info (keyword (:type story)))))

(defn listing-tag-story?
  [story]
  (listing-tag-story-types (keyword (:type story))))

(defn actor-watcher-sets
  [story]
  [(key/actor-watchers (:actor_id story))])

(defn listing-watcher-sets
  [story]
  (when (= :listing (group story))
     (cons (key/listing-watchers (:listing_id story))
           (when (and (listing-tag-story? story) (:tag_ids story))
             (map key/tag-watchers (:tag_ids story))))))

(defn followee-watcher-sets
  [story]
  (when (:followee_id story) [(key/actor-watchers (:followee_id story))]))

(defn watcher-sets
  [story]
  (concat
   (actor-watcher-sets story)
   (listing-watcher-sets story)
   (followee-watcher-sets story)))

(defn interested-users
  [conn story]
  (redis/with-connection conn (apply redis/sunion (watcher-sets story))))

(defn interested-feeds
  ""
  [conn story]
  (map #(key/user-feed % (feed-type (group story)))
       (interested-users conn story)))

(defn actor-story-sets
  [story]
  [(key/format-key (name (feed-type (group story))) "a" (:actor_id story))])

(defn listing-story-sets
  [story]
  (when (= :listing (group story))
     (cons (key/card-listing-story (:listing_id story))
           (when (and (listing-tag-story? story) (:tag_ids story))
             (map key/card-tag-story (:tag_ids story))))))

(defn followee-story-sets
  [story]
  (when (:followee_id story) [(key/network-user-story (:followee_id story))]))

(defn destination-story-sets
  [story]
  (concat
   (actor-story-sets story)
   (listing-story-sets story)
   (followee-story-sets story)))

(defn destination-sets
  [conn story]
  (concat
   (destination-story-sets story)
   (when (= :c (feed-type (group story))) [(key/everything-feed)])
   (interested-feeds conn story)))

;; stories

(defn multi-action-digest
  ([listing-id actor-id actions score]
     {:type "listing_multi_action" :actor_id actor-id :listing_id listing-id :types actions :score score})
  ([listing-id actor-id actions] (multi-action-digest listing-id actor-id actions nil)))

(defn multi-actor-digest
  ([listing-id action actor-ids score]
     {:type "listing_multi_actor" :listing_id listing-id :action action :actor_ids actor-ids :score score})
  ([listing-id action actor-ids] (multi-actor-digest listing-id action actor-ids nil)))

(defn multi-actor-multi-action-digest
  ([listing-id actions score]
     {:type "listing_multi_actor_multi_action" :listing_id listing-id :types actions :score score})
  ([listing-id actions] (multi-actor-multi-action-digest listing-id actions nil)))

