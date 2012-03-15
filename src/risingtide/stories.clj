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

(def short-key
  {:type :t
   :actor_id :aid
   :listing_id :lid
   :tag_id :tid
   :buyer_id :bid
   :followee_id :fid
   :invitee_profile_id :iid
   :text :tx
   :network :n})

(def listing-tag-story-types #{:listing_activated :listing_sold})

(defn group
  [story]
  (:group (type-info (keyword (:type story)))))

(defn interest-tokens
  "returns tuples of keys and interest tokens"
  [story]
  (for [key (group-interest-keys (group story))]
    [key (str (name key) ":" (get story (id-key key)))]))

(defn interest-queries
  "returns a list of interest queries based on user ids an interest tokens

result is ordered by user id and then interest token key like:

user1interestAquery
user1interestBquery
user2interestAquery
user2interestBquery
"
  [user-ids interest-tokens]
  (flatten
   (for [user-id user-ids]
     (for [[key token] interest-tokens]
       (redis/sismember (key/interest user-id (name key)) token)))))

(defn compile-interested-feed-keys
  "given a set of interest booleans in groups of size interest-token-count,
return a list of booleans indicating whether each group contains a true value

so given an interest-token-count of 2, a set of feed keys like

 [\"feed1\" \"feed2\" \"feed3\" \"feed4\"]

and a list of interests like

 [true true, true false, false true, false false]

returns

 [\"feed1\" \"feed2\" \"feed3\"]
"
  [feed-keys interests interest-token-count]
  (let [interested-bools (map #(some identity %) (partition interest-token-count interests))]
    (for [[feed-key interested] (map vector feed-keys interested-bools)
          :when interested]
      feed-key)))

(defn interests
  [conn user-ids interest-tokens]
  (apply redis/with-connection conn (interest-queries user-ids interest-tokens)))

(defn interested-feeds
  "filter the list of feed keys to find those interested in the given story

go through some contortions to take advantage of redis pipelining"
  [conn feed-keys story]
  (let [tokens (interest-tokens story)
        user-ids (map #(get (.split % ":") 3) feed-keys)]
    (compile-interested-feed-keys
     feed-keys (interests conn user-ids tokens) (count tokens))))

(defn listing-tag-story?
  [story]
  (listing-tag-story-types (keyword (:type story))))

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

(defn destination-feeds
  [conn story]
  (let [type-key (feed-type (group story))]
    (concat
     (destination-story-sets story)
     (when (= :c type-key) [(key/everything-feed)])
     (interested-feeds conn (redis/with-connection conn (queries/user-feed-keys "*" (name type-key))) story))))

(defn encode
  "given a story, encode it into a short-key json format suitable for memory efficient storage in redis"
  [story]
  (json/json-str
   (reduce (fn [h [key val]]
             (let [s (short-key key)] (if s (assoc h s val) h)))
           {} story)))
