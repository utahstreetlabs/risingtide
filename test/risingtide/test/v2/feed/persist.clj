(ns risingtide.test.v2.feed.persist
  (:require
   [risingtide.v2.feed.persist :refer :all]
   [risingtide.v2.story :as story]

   [risingtide.test :refer :all]
   [midje.sweet :refer :all]))

(doseq [story [(story/->TagLikedStory 1 2) (story/->ListingLikedStory 1 2 [3] #{:ev})
               (story/->ListingCommentedStory 1 2 [3] "HAMS!" #{:ev})
               (story/->ListingActivatedStory 1 2 [3] #{:ev})
               (story/->ListingSoldStory 1 2 [3] 4 #{:ev})
               (story/->ListingSharedStory 1 2 [3] "facebook" #{:ev})
               
               (story/->MultiActorMultiActionStory 1 #{:listing_liked})
               (story/->MultiActorStory 1 :listing_liked #{2 3})
               (story/->MultiActionStory 1 2 #{:listing_liked})
               (story/->MultiListingStory 1 :listing_liked #{3 4})]]
  
  (fact (str (type story)" should not change during encoding/decoding")
    (decode (encode story)) => story))
