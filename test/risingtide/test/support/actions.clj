(ns risingtide.test.support.actions
  (:require
   [clojure.set :refer [rename-keys]]
   [risingtide
    [core :refer [now]]]))

(def property-aliases {:to :collection_id})

(defn listing-action
  [type actor-id listing-id & {:as props}]
  (merge {:type type :actor_id actor-id :listing_id listing-id :timestamp (now) :feed nil}
         (rename-keys props property-aliases)))

(defmacro listing-action-helper
  [name type]
  `(defn ~name
     [actor-id# listing-id# & props#]
     (apply listing-action ~type actor-id# listing-id# props#)))

(listing-action-helper activates :listing_activated)
(listing-action-helper likes :listing_liked)
(listing-action-helper shares :listing_shared)
(listing-action-helper saves :listing_saved)
(listing-action-helper sells :listing_sold)
(listing-action-helper comments-on :listing_commented)

(defn likes-tag
  ([actor-id tag-id timestamp]
     {:type :tag_liked :actor_id actor-id :tag_id tag-id :timestamp timestamp :feed nil})
  ([actor-id tag-id] (likes-tag actor-id tag-id (now))))

(defn hi [& {:as args}]
  (prn args))

(apply hi [])