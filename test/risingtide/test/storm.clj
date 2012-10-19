(ns risingtide.test.storm
  (:require
   [midje.sweet :refer :all]
   [backtype.storm [testing :refer
                    [with-local-cluster ms= complete-topology read-tuples]]]
   [risingtide.v2.story :refer [->ListingLikedStory ->ListingCommentedStory ->ListingActivatedStory]]
   [risingtide.storm.core :refer [feed-generation-topology]]))

;; failing with:
;; java.lang.RuntimeException: java.lang.ClassNotFoundException:
;; risingtide.v2.story.ListingLikedStory
;; wtf?
#_(with-local-cluster [cluster]
  (let [inputs [[(->ListingLikedStory :listing_liked 1 2 [3, 4] [:ev] 1)]
                #_[(->ListingCommentedStory :listing_commented 1 2 [3, 4] "HI" [:ev] 1)]
                #_[(->ListingActivatedStory :listing_activated 1 2 [3, 4] [:ev] 1)]]
        results (complete-topology cluster
                                   (feed-generation-topology)
                                   :mock-sources {"stories" inputs}
                                   )]
    (fact (read-tuples results "interest-reducer") =>
          (ms= [["first transformed"] ["second transformed"]]))))


