(ns risingtide.test.story
  (:require
   [risingtide.story :refer :all]

   [risingtide.test :refer :all]
   [midje.sweet :refer :all]))

(facts "About score metadata"
  (score {}) => nil
  (score (with-score {} 2)) => 2
  ;; preserves existing metadata
  (:flavor (meta (with-score (with-meta {} {:flavor :chunky}) 3))) => :chunky)

(add (->MultiActorMultiActionStory bacon {:listing_liked #{jim} :listing_shared #{jim} :listing_sold #{cutter}})
     (->ListingCommentedStory jim ham [] "this iz grate" nil))

(facts "about adding stories to multi actor multi action digest stories"
  (let [mama (with-score (->MultiActorMultiActionStory bacon {:listing_liked #{jim} :listing_shared #{jim} :listing_sold #{cutter}}) 1)
        comment (with-score (->ListingCommentedStory jim bacon [] "this iz grate" nil) 2)
        mama-plus-comment (add mama comment)]
    
    mama-plus-comment => (->MultiActorMultiActionStory bacon {:listing_liked #{jim} :listing_shared #{jim} :listing_sold #{cutter} :listing_commented #{jim}})
    (score mama-plus-comment) => (score comment)))

(facts "about adding stories to multi action digest stories"
  (let [multi-action (with-score (->MultiActionStory bacon jim #{:listing_liked :listing_shared}) 1)
        comment (with-score (->ListingCommentedStory jim bacon [] "this iz grate" nil) 2)
        multi-action-plus-comment (add multi-action comment)

        cutter-sold (with-score (->ListingSoldStory cutter bacon [] jim nil) 3)
        multi-action-plus-cutter-sold (add multi-action cutter-sold)]
    
    multi-action-plus-comment => (->MultiActionStory bacon jim #{:listing_liked :listing_shared :listing_commented})
    (score multi-action-plus-comment) => (score comment)

    multi-action-plus-cutter-sold =>
    (->MultiActorMultiActionStory bacon {:listing_liked #{jim} :listing_shared #{jim} :listing_sold #{cutter}})

    (score multi-action-plus-cutter-sold) => (score cutter-sold)))

(facts "about adding stories to multi actor digest stories"
  (let [multi-actor (with-score (->MultiActorStory bacon :listing_commented #{jim cutter}) 1)
        comment (with-score (->ListingCommentedStory rob bacon [] "hi" nil) 2)
        multi-actor-plus-comment (add multi-actor comment)

        sold (with-score (->ListingSoldStory rob bacon [] jim nil) 2)
        multi-actor-plus-sold (add multi-actor sold)]

    multi-actor-plus-comment => (->MultiActorStory bacon :listing_commented #{jim cutter rob})
    (score multi-actor-plus-comment) => (score comment)
    
    multi-actor-plus-sold => (->MultiActorMultiActionStory bacon {:listing_commented #{jim cutter} :listing_sold #{rob}})
    (score multi-actor-plus-sold) => (score sold)))

(facts "about adding stories to multi listing digest stories"
  (let [multi-listing (with-score (->MultiListingStory jim :listing_liked #{bacon ham}) 1)
        like-toast (with-score (->ListingLikedStory jim toast [] nil) 2)
        multi-listing-plus-toast (add multi-listing like-toast)]
    
    multi-listing-plus-toast => (->MultiListingStory jim :listing_liked #{bacon ham toast})
    (score multi-listing-plus-toast) => (score like-toast)))


