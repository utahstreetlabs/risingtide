(ns risingtide.test.support.actions
  (:require
   [risingtide
    [core :refer [now]]]))

(defn listing-action
  ([type actor-id listing-id args]
     (merge {:type type :actor_id actor-id :listing_id listing-id} args))
  ([type actor-id listing-id] (listing-action type actor-id listing-id nil)))

(defmacro listing-action-helper
  [name type]
  `(defn ~name
     ([actor-id# listing-id# args#]
        (listing-action ~type actor-id# listing-id# args#))
     ([actor-id# listing-id#] (~name actor-id# listing-id# {:timestamp (now) :feed nil}))))

(listing-action-helper activates :listing_activated)
(listing-action-helper likes :listing_liked)
(listing-action-helper shares :listing_shared)
(listing-action-helper sells :listing_sold)
(listing-action-helper comments-on :listing_commented)

(defn likes-tag
  ([actor-id tag-id timestamp]
     {:type :tag_liked :actor_id actor-id :tag_id tag-id :timestamp timestamp :feed nil})
  ([actor-id tag-id] (likes-tag actor-id tag-id (now))))

