(ns risingtide.storm.build-feed
  (:require [risingtide.model.story :refer [->ListingLikedStory]]

            ;; serialization
            [risingtide.feed.persist :refer [encode-feed]]

            [risingtide.storm
             [story-spout :refer [resque-spout]]
             [record-bolt :refer [record-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer interest-reducer]]
             [feed-bolts :refer [add-to-feed add-to-curated-feed]]
             [drpc :as drpc]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack! topology spout-spec bolt-spec]] [config :refer [TOPOLOGY-DEBUG]]])
  (:import [backtype.storm LocalCluster LocalDRPC]
           [backtype.storm.coordination BatchBoltExecutor]
           [risingtide FeedBuilder]))

(defn find-cands [tuple collector]
  (let [user-id (.getString tuple 1)]
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 2 23 [3 4] nil)])
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 3 23 [3 4] nil)])))

(defbolt find-candidate-stories ["id" "user-id" "story"] [tuple collector]
  (find-cands tuple collector)
  (ack! collector tuple))

(defn serialize [{id "id" feed "feed"} collector]
  (emit-bolt! collector [id (with-out-str (print (encode-feed feed)))]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))

(defn feed-build-topology [drpc]
  (topology (drpc/topology-spouts drpc "build-feed" "feed-build-requests")
            (drpc/topology-bolts
             "feed-build-requests"
             ["records" find-candidate-stories]
             {"likes" [{"records" :shuffle}
                       like-interest-scorer
                       :p 2]
              "follows" [ {"records" :shuffle}
                          follow-interest-scorer
                          :p 2]}
             {"interest-reducer" [{"likes" ["user-id" "story"]
                                   "follows" ["user-id" "story"]}
                                  interest-reducer
                                  :p 5]

              "feed-builder"  [{["interest-reducer" "story"] ["id" "user-id"]}
                               (BatchBoltExecutor. (FeedBuilder. "story" "user-id"))
                               :p 1]

              "serialize-feed" [{"feed-builder" ["id" "user-id"]}
                                serialize-feed
                                :p 1]

              }
             ["serialize-feed" "feed"])) )

(comment
  (def c (LocalCluster.))
  (def d (LocalDRPC.))
  (.submitTopology c "build-feed" {TOPOLOGY-DEBUG true} (feed-build-topology d))
  (.execute d "build-feed" "1")
  (.shutdown c)
1
  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )