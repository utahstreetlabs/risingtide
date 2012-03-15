(ns risingtide.test.stories
  (:use [risingtide.stories]
        [risingtide.core :only [env]])
  (:use [midje.sweet])
  (:require [risingtide.key :as key]))

(def nas key/network-actor-story)
(def nus key/network-user-story)
(def nts key/network-tag-story)
(def nls key/network-listing-story)
(def cas key/card-actor-story)
(def cts key/card-tag-story)
(def cls key/card-listing-story)

(facts
  (compile-interested-feed-keys
   ["feed1" "feed2" "feed3" "feed4" "feed5"]
   [true true, true false, false true, false false, true true] 2) => ["feed1" "feed2" "feed3" "feed5"]

   (let [conn {}
         story {:type :listing_activated :actor_id 1 :listing_id 3}
         tokens (interest-tokens story)]
     (interested-feeds conn ["magt:f:u:1:c" "magt:f:u:2:c" "magt:f:u:3:c"] story) => ["magt:f:u:1:c" "magt:f:u:2:c"]
     (provided
       (interests {} ["1" "2" "3"] tokens) => [true true, true false, false false]))

   (actor-story-sets {:type "listing_activated" :actor_id 1}) => [(cas 1)]
   (actor-story-sets {:type "user_joined" :actor_id 1}) => [(nas 1)]

   (listing-story-sets {:type "listing_activated" :listing_id 1} ) => [(cls 1)]
   (listing-story-sets {:type "listing_liked" :listing_id 1} ) => [(cls 1)]
   (listing-story-sets {:type "listing_activated" :listing_id 1 :tag_ids [3 4]} ) => [(cls 1) (cts 3) (cts 4)]
   (listing-story-sets {:type "listing_liked" :listing_id 1 :tag_ids [3 4]} ) => [(cls 1)]
   (listing-story-sets {:type "user_joined" :actor_id 1} ) => nil

   (followee-story-sets {:type "user_joined" :followee_id 1}) => [(nus 1)]
   (followee-story-sets {:type "listing_activated"}) => nil

   (against-background (env) => :test))
