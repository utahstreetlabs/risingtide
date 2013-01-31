(ns risingtide.test.serializers
  (:require
   [risingtide.model.story :refer [->TagLikedStory ->ListingLikedStory ->ListingCommentedStory
                                   ->ListingActivatedStory ->ListingSoldStory ->ListingSharedStory
                                   ->ListingSavedStory
                                   ->MultiActorMultiActionStory ->MultiActionStory
                                   ->MultiActorStory ->MultiListingStory]]
   [carbonite
    [api :refer [default-registry register-serializers]]
    [buffer :refer [read-bytes write-bytes]]]
   [risingtide.test :refer [serialize-deserialize]]
   [midje.sweet :refer :all]))

(doseq [story [(with-meta (->TagLikedStory 1 2) {:timestamp 4})
               (with-meta (->ListingLikedStory 1 2 [3 4] [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingCommentedStory 1 2 [3 4] "hamburgers" [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingActivatedStory 1 2 [3 4] [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingSoldStory 1 2 [3 4] 5 [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingSharedStory 1 2 [3 4] :facebook [:ev :ylf]) {:timestamp 4})
               (with-meta (->ListingSavedStory 1 2 [3 4] 5 [:ev :ylf]) {:timestamp 4})

               (with-meta (->MultiActorMultiActionStory 1 {:listing_liked #{1} :listing_shared #{1}}) {:timestamp 4})
               (with-meta (->MultiActorStory 1 :listing_liked [2 3 4]) {:timestamp 4})
               (with-meta (->MultiActionStory 1 2 [:listing_liked :listing_shared]) {:timestamp 4})
               (with-meta (->MultiListingStory 1 :listing_liked [1 2 3 4] 30) {:timestamp 4})

               (with-meta (->ListingActivatedStory 1 2 [3 4] [:ev :ylf]) {:timestamp 4})]]

  (fact (str (class story)" serializes and deserializes into itself")
    (serialize-deserialize story)
    => story)

  (fact (str (class story)" serializes and deserializes metadata")
    (meta (serialize-deserialize story))
    => (meta story)))
