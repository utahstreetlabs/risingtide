(ns risingtide.v2.jobs
  (require [clojure.string :as str]
           [risingtide.core :refer [now]]
           [risingtide.v2.story :as s]
           [risingtide.v2.feed-set :as feed-set])
)

(def factories
  {:listing_liked s/map->ListingLikedStory
   :listing_commented s/map->ListingCommentedStory
   :listing_activated s/map->ListingActivatedStory
   :listing_sold s/map->ListingSoldStory
   :listing_shared s/map->ListingSharedStory
   :tag_liked s/map->TagLikedStory})

(defn dash-case-keys [story]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) story)))

(defn story-to-record [story score]
  ((factories (:type story)) (dash-case-keys (assoc story :score score))))

(defn add-story!
  [redii story]
  (feed-set/add
   (story-to-record story (now))))
