(ns risingtide.storm.core
  (:require [risingtide.config :as config]
            [risingtide.storm
             [action-spout :refer [resque-spout]]
             [story-bolts :refer [create-story-bolt]]
             [action-bolts :refer [prepare-action-bolt save-action-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer
                                      seller-follow-interest-scorer interest-reducer]]
             [feed-bolts :refer [add-to-feed add-to-curated-feed]]
             [build-feed :as feed-building]]
            [risingtide.storm.drpc.local-server :as local-drpc-server]
            [backtype.storm [clojure :refer [topology spout-spec bolt-spec]] [config :refer [TOPOLOGY-DEBUG DRPC-SERVERS]]])
  (:import [backtype.storm LocalCluster LocalDRPC]))

(defn feed-generation-topology
  ([] (feed-generation-topology nil))
  ([drpc]
     (topology
      (merge {"actions" (spout-spec resque-spout)} (feed-building/spouts drpc))

      (merge
       {"prepare-actions" (bolt-spec {"actions" :shuffle}
                                     prepare-action-bolt)

        "save-actions" (bolt-spec {"actions" :shuffle}
                                  save-action-bolt)

        "stories" (bolt-spec {"prepare-actions" :shuffle}
                             create-story-bolt)

        ;; everything feed
        "curated-feed" (bolt-spec {"stories" :global}
                                  add-to-curated-feed
                                  :p 1)

        ;; user feeds

        "active-users" (bolt-spec {"stories" :shuffle}
                                  active-user-bolt
                                  :p 1)

        "likes" (bolt-spec {"active-users" :shuffle}
                           like-interest-scorer
                           :p 2)
        "follows" (bolt-spec {"active-users" :shuffle}
                             follow-interest-scorer
                             :p 2)
        "seller-follows" (bolt-spec {"active-users" :shuffle}
                                    seller-follow-interest-scorer
                                    :p 2)

        "interest-reducer" (bolt-spec {"likes" ["user-id" "story"]
                                       "follows" ["user-id" "story"]
                                       "seller-follows" ["user-id" "story"]}
                                      interest-reducer
                                      :p 5)

        "add-to-feed" (bolt-spec {"interest-reducer" ["user-id"]
                                  "drpc-interest-reducer" ["user-id"]}
                                 add-to-feed
                                 :p 20)}
       (feed-building/bolts)))))

(defn run-local! []
  (let [drpc (LocalDRPC.)]
    (doto (LocalCluster.)
      (.submitTopology "story"
                       {
                        ;; enable during development for easy debugging
                        ;; TOPOLOGY-DEBUG true
                       }
                      (feed-generation-topology drpc)))
    (local-drpc-server/run! drpc (config/local-drpc-port))))


(comment
  (def c (LocalCluster.))
  (def d (LocalDRPC.))
  (def dr (local-drpc-server/run! d 3772))
  (.submitTopology c "build-feed" {TOPOLOGY-DEBUG true} (feed-generation-topology d))
  (import 'backtype.storm.utils.DRPCClient)
  (def dc (DRPCClient. "localhost" 3772))
  (.execute dc "build-feed" "47")
  (.shutdown c)
  (.stop dr)

  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )

