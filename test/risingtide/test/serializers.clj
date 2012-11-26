(ns risingtide.test.serializers
  (:require [risingtide.model.story :refer [->TagLikedStory ->ListingLikedStory ->ListingCommentedStory
                                            ->ListingActivatedStory ->ListingSoldStory ->ListingSharedStory]]

            [carbonite
             [api :refer [default-registry register-serializers]]
             [buffer :refer [read-bytes write-bytes]]]
            risingtide.test
            [midje.sweet :refer :all])

  (:import [risingtide.serializers TagLikedStory ListingLikedStory ListingCommentedStory
            ListingActivatedStory ListingSoldStory ListingSharedStory]))


(def registry (doto (default-registry)
                (register-serializers {risingtide.model.story.TagLikedStory (risingtide.serializers.TagLikedStory.)
                                       risingtide.model.story.ListingLikedStory (risingtide.serializers.ListingLikedStory.)
                                       risingtide.model.story.ListingCommentedStory (risingtide.serializers.ListingCommentedStory.)
                                       risingtide.model.story.ListingActivatedStory (risingtide.serializers.ListingActivatedStory.)
                                       risingtide.model.story.ListingSoldStory (risingtide.serializers.ListingSoldStory.)
                                       risingtide.model.story.ListingSharedStory (risingtide.serializers.ListingSharedStory.)
                                       })))

(defn sds [story]
  (->> story
       (write-bytes registry)
       (read-bytes registry)))

(meta (->ListingLikedStory 1 2 [3 4] [:ev :ylf]))
(doseq [story [(with-meta (->TagLikedStory 1 2) {:timestamp 4})
               (with-meta (->ListingLikedStory 1 2 [3 4] [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingCommentedStory 1 2 [3 4] "hamburgers" [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingActivatedStory 1 2 [3 4] [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingSoldStory 1 2 [3 4] 5 [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingSharedStory 1 2 [3 4] :facebook [:ev :ylf]) {:timestamp 4})]]

  (fact (str (class story)" serializes and deserializes into itself")
    (sds story)
    => story)

  (fact (str (class story)" serializes and deserializes metadata")
    (meta (sds story))
    => (meta story)))



