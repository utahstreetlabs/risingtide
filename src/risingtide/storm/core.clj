(ns risingtide.storm.core
  (:require [risingtide.storm
             [story-spout :refer [resque-spout]]
             [record-bolt :refer [record-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer interest-reducer]]
             [feed-bolts :refer [add-to-feed add-to-curated-feed]]]
            [backtype.storm [clojure :refer [topology spout-spec bolt-spec]] [config :refer [TOPOLOGY-DEBUG]]])
  (:import [backtype.storm LocalCluster]))

(defn feed-generation-topology []
  (topology
   {"stories" (spout-spec resque-spout)}

   ;; everything feed

   {"records" (bolt-spec {"stories" :shuffle}
                         record-bolt)

    "curated-feed" (bolt-spec {"records" :global}
                              add-to-curated-feed
                              :p 1)

    ;; user feeds

    "active-users" (bolt-spec {"records" :shuffle}
                              active-user-bolt
                              :p 1)

    "likes" (bolt-spec {"active-users" :shuffle}
                       like-interest-scorer
                       :p 2)
    "follows" (bolt-spec {"active-users" :shuffle}
                         follow-interest-scorer
                         :p 2)

    "interest-reducer" (bolt-spec {"likes" ["user-id" "story"]
                                   "follows" ["user-id" "story"]}
                                  interest-reducer
                                  :p 5)

    "add-to-feed" (bolt-spec {"interest-reducer" ["user-id"]}
                             add-to-feed
                             :p 20)
  }))

(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "story" {TOPOLOGY-DEBUG true} (feed-generation-topology))))


(comment
  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )