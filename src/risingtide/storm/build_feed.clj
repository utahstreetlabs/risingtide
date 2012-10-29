(ns risingtide.storm.build-feed
  (:require [risingtide.model.story :refer [->ListingLikedStory]]
            [risingtide.storm
             [action-spout :refer [resque-spout]]
             [recent-stories-bolt :refer [recent-stories-bolt]]
             [story-bolts :refer [create-story-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer interest-reducer]]
             [feed-bolts :refer [serialize-feed]]
             [drpc :as drpc]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack! topology]] [config :refer [TOPOLOGY-DEBUG]]])
  (:import [backtype.storm LocalCluster LocalDRPC]
           [backtype.storm.coordination BatchBoltExecutor]
           [risingtide FeedBuilder]))

(defn find-cands [tuple collector]
  ;; the second value in the tuple coming off a drpc spout will be the
  ;; argument passed by the client
  (let [user-id (.getString tuple 1)]
    ;; the first value in the tuple coming off a drpc spout will be
    ;; the request id
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 2 23 [3 4] nil)])
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 3 23 [3 4] nil)])))

(defbolt find-candidate-stories ["id" "user-id" "story"] [tuple collector]
  (find-cands tuple collector)
  (ack! collector tuple))

(defn spouts [drpc]
  (drpc/topology-spouts drpc "build-feed" "drpc-feed-build-requests"))

(defn bolts []
  (drpc/topology-bolts
   "drpc-feed-build-requests"
   ["drpc-stories" recent-stories-bolt]
   {"drpc-records" [{"drpc-stories" :shuffle} create-story-bolt]}
   {"drpc-likes" [{"drpc-records" :shuffle}
                  like-interest-scorer
                  :p 2]

    "drpc-follows" [{"drpc-records" :shuffle}
                   follow-interest-scorer
                   :p 2]

    "drpc-interest-reducer" [{"drpc-likes" ["user-id" "story"]
                              "drpc-follows" ["user-id" "story"]}
                             interest-reducer
                             :p 5]

    "drpc-feed-builder"  [{["drpc-interest-reducer" "story"] ["id" "user-id"]}
                             (BatchBoltExecutor. (FeedBuilder. "story" "user-id"))
                             :p 1]

    "drpc-serialize-feed" [{"drpc-feed-builder" ["id" "user-id"]}
                           serialize-feed
                           :p 1]

    }
   ["drpc-serialize-feed" "feed"]))

(defn feed-build-topology [drpc]
  (topology (spouts drpc) (bolts)))

(comment
  (def c (LocalCluster.))
  (def d (LocalDRPC.))
  (.submitTopology c "build-feed" {TOPOLOGY-DEBUG true} (feed-build-topology d))
  (.execute d "build-feed" "1")
  (.shutdown c)

  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )