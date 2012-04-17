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

(def group-feed-type
  {:user :network :listing :card :tag :card})

;; a map from group names to feed type tokens suitable for
;; constructing feed keys. currently looks like:
;; {:user "n" :listing "c" :tag "c"}
(def group-feed-type-key-token
  (reduce (fn [m [group-name feed-type]] (assoc m group-name (str (first-char feed-type))))
          {} group-feed-type))

(defn group
  [story]
  (:group (type-info (keyword (:type story)))))

(defn feed-type
  [story]
  (group-feed-type (group story)))

(defn feed-type-key-token
  [story]
  (group-feed-type-key-token (group story)))

(def id-key
  {:a :actor_id :l :listing_id :t :tag_id})

(def listing-tag-story-types #{:listing_activated :listing_sold})

(def short-key
  {:feed :f
   :action :a
   :type :t
   :types :ts
   :actor_id :aid
   :actor_ids :aids
   :listing_id :lid
   :listing_ids :lids
   :tag_id :tid
   :buyer_id :bid
   :followee_id :fid
   :invitee_profile_id :iid
   :text :tx
   :network :n})

(def long-key (reduce (fn [hash [key val]] (assoc hash val key)) {} short-key))

(defn translate-keys
  [hash translator]
  (reduce (fn [h [key val]] (let [s (translator key)] (if s (assoc h s val) h))) {} hash))

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str (translate-keys story short-key)))

(defn decode
  "given a short-key json encoded story, decode into a long keyed hash"
  [story]
  (translate-keys (json/read-json story) long-key))

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
  [redii story]
  (redis/with-connection (:interests redii) (apply redis/sunion (watcher-sets story))))

(defn interested-feeds
  ""
  [redii story]
  (map #(key/user-feed % (feed-type-key-token story))
       (interested-users redii story)))

(defn actor-story-sets
  [story]
  [(key/format-key (feed-type-key-token story) "a" (:actor_id story))])

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
  [redii story]
  (concat
   (destination-story-sets story)
   (when (= :card (feed-type story)) [(key/everything-feed)])
   (interested-feeds redii story)))

(defn add!
  [redii story time]
  (apply redis/with-connection (:stories redii)
         (map #(redis/zadd % time (encode story))
              (destination-story-sets story))))

;; finding existing stories

(defn range-with-scores
  [redii start-score end-score]
  (let [stories-and-scores-queries
        (map #(redis/zrangebyscore % start-score end-score "WITHSCORES")
             (redis/with-connection (:stories redii) (queries/story-keys)))]
    (if (empty? stories-and-scores-queries)
      []
      (partition 2 (apply concat
                          (apply redis/with-connection (:stories redii) stories-and-scores-queries))))))


;; creating new stories

(defn- stash-encoded
  [s]
  (assoc s :encoded (encode s)))

(def digest-types
  {:lmt "listing_multi_action"
   :lma "listing_multi_actor"
   :lmama "listing_multi_actor_multi_action"
   :aml "actor_multi_listing"})

(def digest-type-names
  (into #{} (vals digest-types)))

(defn digest-story?
  [story]
  (digest-type-names (:type story)))

(defn multi-action-digest
  ([listing-id actor-id actions score]
     (stash-encoded {:type (digest-types :lmt) :actor_id actor-id :listing_id listing-id :types actions :score score}))
  ([listing-id actor-id actions] (multi-action-digest listing-id actor-id actions nil)))

(defn multi-actor-digest
  ([listing-id action actor-ids score]
     (stash-encoded {:type (digest-types :lma) :listing_id listing-id :action action :actor_ids actor-ids :score score}))
  ([listing-id action actor-ids] (multi-actor-digest listing-id action actor-ids nil)))

(defn multi-actor-multi-action-digest
  ([listing-id actions score]
     (stash-encoded {:type (digest-types :lmama) :listing_id listing-id :types actions :score score}))
  ([listing-id actions] (multi-actor-multi-action-digest listing-id actions nil)))

(defn multi-listing-digest
  ([actor-id action listing-ids score]
     (stash-encoded {:type (digest-types :aml) :actor_id actor-id :action action :listing_ids listing-ids :score score}))
  ([actor-id action listing-ids] (multi-listing-digest actor-id action listing-ids nil)))
