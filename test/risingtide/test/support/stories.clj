(ns risingtide.test.support.stories
  (:require
   [risingtide
    [core :refer [now]]]
   [risingtide.model.story :refer :all]))

(defn story [args]
  ((story-factory-for (:type args)) args))

(defn listing-story
  ([type actor-id listing-id args]
     (story (merge {:type type :actor_id actor-id :listing_id listing-id} args)))
  ([type actor-id listing-id] (listing-story type actor-id listing-id nil)))

(defmacro listing-story-helper
  [name type]
  `(defn ~name
     ([actor-id# listing-id# args#]
        (listing-story ~type actor-id# listing-id# args#))
     ([actor-id# listing-id#] (~name actor-id# listing-id# {}))))

(defn tag-liked [actor-id tag-id]
  (->TagLikedStory actor-id tag-id))

(defn listing-liked [actor-id listing-id tag-ids feed]
  (->ListingLikedStory actor-id listing-id tag-ids feed))

(defn listing-commented [actor-id listing-id tag-ids text feed]
  (->ListingCommentedStory actor-id listing-id tag-ids text feed))

(defn listing-activated [actor-id listing-id tag-ids feed]
  (->ListingActivatedStory actor-id listing-id tag-ids feed))

(defn listing-sold [actor-id listing-id tag-ids buyer-id feed]
  (->ListingSoldStory actor-id listing-id tag-ids buyer-id feed))

(defn listing-shared [actor-id listing-id tag-ids network feed]
  (->ListingSharedStory actor-id listing-id tag-ids network feed))

(defn multi-actor-multi-action-story [listing-id actions]
  (->MultiActorMultiActionStory listing-id actions))

(defn multi-action-story [listing-id actor-id actions]
  (->MultiActionStory listing-id actor-id actions))

(defn multi-actor-story [listing-id action actor-ids]
  (->MultiActorStory listing-id action actor-ids))

(defn multi-listing-story [actor-id action listing-ids count]
  (->MultiListingStory actor-id action listing-ids count))

