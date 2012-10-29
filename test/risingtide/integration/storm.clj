(ns risingtide.integration.storm
  (:require
   [risingtide.model.story :refer [->ListingLikedStory ->ListingCommentedStory ->ListingActivatedStory]]
   [risingtide.storm.core :refer [feed-generation-topology]]

   [risingtide.test :refer :all]
   [risingtide.integration.support :refer :all]
   [backtype.storm [testing :refer
                    [with-local-cluster with-simulated-time-local-cluster ms= complete-topology read-tuples]]]
   [midje.sweet :refer :all])
  (:import [backtype.storm LocalDRPC]))

(def drpc (LocalDRPC.))

(defn complete-feed-generation-topology [& {actions :actions feed-build-requests :feed-builds
                                            :or {actions [] feed-build-requests []}}]
  (with-local-cluster [cluster]
    (complete-topology cluster
                       (feed-generation-topology drpc)
                       :mock-sources {"actions" (map vector actions)
                                      "drpc-feed-build-requests" feed-build-requests})))

(def actions
  (on-copious
   (jim activates bacon)
   (jim likes ham)
   (jim likes toast)
   (jim shares toast)
   (cutter likes breakfast-tacos)))

(def results
  (complete-feed-generation-topology :actions actions))

(fact ""
  (read-tuples results "records") =>
  [[nil #risingtide.model.story.ListingActivatedStory{:actor-id 1, :listing-id 100, :tag-ids nil, :feed ()}]
   [nil #risingtide.model.story.ListingLikedStory{:actor-id 1, :listing-id 101, :tag-ids nil, :feed ()}]
   [nil #risingtide.model.story.ListingLikedStory{:actor-id 1, :listing-id 105, :tag-ids nil, :feed ()}]
   [nil #risingtide.model.story.ListingSharedStory{:actor-id 1, :listing-id 105, :tag-ids nil, :network nil, :feed ()}]
   [nil #risingtide.model.story.ListingLikedStory{:actor-id 6, :listing-id 104, :tag-ids nil, :feed ()}]])




(comment
  (require '[storm.test.visualization :refer [visualize-topology]])
(visualize-topology (feed-generation-topology drpc))
  )