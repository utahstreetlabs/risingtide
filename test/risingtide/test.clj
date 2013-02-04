(ns risingtide.test
  (:require
   [risingtide.core :refer :all]
   [risingtide.config]
   [risingtide.model
    [story :as story]
    [timestamps :refer [with-timestamp timestamp]]]
   [carbonite
    [api :refer [default-registry register-serializers]]
    [buffer :refer [read-bytes write-bytes]]]
   risingtide.model.story
   [midje.sweet :refer :all])
  (:import [risingtide.serializers TagLikedStory ListingLikedStory ListingCommentedStory
            ListingActivatedStory ListingSoldStory ListingSharedStory ListingSavedStory]))

(alter-var-root #'risingtide.config/env (constantly :test))

(defmacro expose
  "def a variable in the current namespace. This can be used to expose a private function."
  [& vars]
  `(do
     ~@(for [var vars]
         `(def ~(symbol (name var)) (var ~var)))))



(def serialization-registry
  (doto (default-registry)
    (register-serializers {risingtide.model.story.TagLikedStory (risingtide.serializers.TagLikedStory.)
                           risingtide.model.story.ListingLikedStory (risingtide.serializers.ListingLikedStory.)
                           risingtide.model.story.ListingCommentedStory (risingtide.serializers.ListingCommentedStory.)
                           risingtide.model.story.ListingActivatedStory (risingtide.serializers.ListingActivatedStory.)
                           risingtide.model.story.ListingSoldStory (risingtide.serializers.ListingSoldStory.)
                           risingtide.model.story.ListingSharedStory (risingtide.serializers.ListingSharedStory.)
                           risingtide.model.story.ListingSavedStory (risingtide.serializers.ListingSavedStory.)
                           risingtide.model.story.MultiActorMultiActionStory (risingtide.serializers.MultiActorMultiActionStory.)
                           risingtide.model.story.MultiActionStory (risingtide.serializers.MultiActionStory.)
                           risingtide.model.story.MultiActorStory (risingtide.serializers.MultiActorStory.)
                           risingtide.model.story.MultiListingStory (risingtide.serializers.MultiListingStory.)})))

(defn serialize-deserialize [story]
  (->> story
       (write-bytes serialization-registry)
       (read-bytes serialization-registry)))

