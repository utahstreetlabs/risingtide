(ns risingtide.integration.storm
  (:require
   [midje.sweet :refer :all]
   [backtype.storm [testing :refer
                    [with-local-cluster with-simulated-time-local-cluster ms= complete-topology read-tuples]]]
   [risingtide.model.story :refer [->ListingLikedStory ->ListingCommentedStory ->ListingActivatedStory]]
   [risingtide.storm.core :refer [feed-generation-topology]]))

(with-local-cluster [cluster]
  (let [inputs [[{:type "listing_liked" :actor-id 1 :listing-id 2 :tag-ids [1 2]}]]
        results (complete-topology cluster
                                   (feed-generation-topology)
                                   :mock-sources {"stories" inputs}
                                   )]
    (fact
      (read-tuples results "records") =>
      [[#risingtide.model.story.ListingLikedStory{:actor-id 1, :listing-id 2, :tag-ids [1 2], :feed ()}]])))












