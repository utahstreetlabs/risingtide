(ns risingtide.storm.build-feed
  (:require [risingtide.model.story :refer [->ListingLikedStory]]
            [risingtide.storm
             [story-spout :refer [resque-spout]]
             [record-bolt :refer [record-bolt]]
             [active-user-bolt :refer [active-user-bolt]]
             [interests-bolts :refer [like-interest-scorer follow-interest-scorer interest-reducer]]
             [feed-bolts :refer [add-to-feed add-to-curated-feed]]]
            [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack! topology spout-spec bolt-spec]] [config :refer [TOPOLOGY-DEBUG]]])
  (:import [backtype.storm LocalCluster LocalDRPC Constants]
           [backtype.storm.drpc PrepareRequest DRPCSpout JoinResult ReturnResults]
           [backtype.storm.coordination CoordinatedBolt CoordinatedBolt$SourceArgs BatchBoltExecutor]
           [backtype.storm.topology.base BaseBatchBolt]
           [risingtide FeedBuilder]))

(def prepare-request-id "prepare-request")

(defn- drpc-bolt-direct-streams [input-names]
  (reduce (fn [h name] (assoc h [name Constants/COORDINATED_STREAM_ID] :direct))
          {} input-names))

(defn drpc-source-bolts [inputs]
  (map #(if (vector? %) (first %) %) (keys inputs)))

(defn- drpc-bolt-inputs [inputs]
  (if inputs
    (merge inputs (drpc-bolt-direct-streams (drpc-source-bolts inputs)))
    {[prepare-request-id PrepareRequest/ARGS_STREAM] :none}))

(defn- drpc-bolt-sources [inputs source-type]
  (reduce (fn [h name] (assoc h name source-type))
          {} (drpc-source-bolts inputs)))

(defn drpc-bolt-spec [inputs bolt source-type & kwargs]
  (apply bolt-spec
         (merge inputs (drpc-bolt-inputs inputs))
         (CoordinatedBolt. bolt (drpc-bolt-sources inputs source-type) nil)
         kwargs))

(defn find-cands [tuple collector]
  (let [user-id (.getString tuple 1)]
    (prn "HOHO" user-id)
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 2 23 [3 4] nil)])
    (emit-bolt! collector [(.getValue tuple 0) user-id (->ListingLikedStory 3 23 [3 4] nil)])))

(defbolt find-candidate-stories ["id" "user-id" "story"] [tuple collector]
  (find-cands tuple collector)
  (ack! collector tuple))

(defn serialize [{id "id" feed "feed"} collector]
  (prn "HIHI" id feed)
  (emit-bolt! collector [id (with-out-str (prn (seq feed)))]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))

(defn feed-build-topology [drpc]
    (topology
     {"feed-build-requests" (spout-spec (DRPCSpout. "build-feed" drpc))}

     {prepare-request-id (bolt-spec {"feed-build-requests" :none} (PrepareRequest.))



      "records" (drpc-bolt-spec nil find-candidate-stories nil)

      "likes" (drpc-bolt-spec {"records" :shuffle}
                              like-interest-scorer
                              (CoordinatedBolt$SourceArgs/single)
                              :p 2)
      "follows" (drpc-bolt-spec {"records" :shuffle}
                                follow-interest-scorer
                                (CoordinatedBolt$SourceArgs/single)
                                :p 2)

      "interest-reducer" (drpc-bolt-spec {"likes" ["user-id" "story"]
                                          "follows" ["user-id" "story"]}
                                         interest-reducer
                                         (CoordinatedBolt$SourceArgs/all)
                                         :p 5)

      "feed-builder" (drpc-bolt-spec {["interest-reducer" "story"] ["id" "user-id"]}
                                     (BatchBoltExecutor. (FeedBuilder. "story" "user-id"))
                                     (CoordinatedBolt$SourceArgs/all)
                                     :p 1)

      "serialize-feed" (drpc-bolt-spec {"feed-builder" ["id" "user-id"]}
                                       serialize-feed
                                       (CoordinatedBolt$SourceArgs/all)
                                       :p 1)



      "join-result" (bolt-spec {[prepare-request-id PrepareRequest/RETURN_STREAM] ["request"]
                                "serialize-feed" ["feed"]} ;; must match last bolt, must be a 1-tuple
                               (JoinResult. prepare-request-id))

      "return-results" (bolt-spec {"join-result" :none}
                                  (ReturnResults.))

      }))
(comment
  (def c (LocalCluster.))
  (def d (LocalDRPC.))
  (.submitTopology c "hash-bang" {TOPOLOGY-DEBUG true} (feed-build-topology d))
  (.execute d "build-feed" "1")
  (.shutdown c)

  ;; lein run -m risingtide.storm.core/run-local!
  ;; brooklyn:
  ;; User.inject_listing_story(:listing_liked, 2, Listing.find(23))
  )


