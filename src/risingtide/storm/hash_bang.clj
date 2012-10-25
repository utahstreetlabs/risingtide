(ns risingtide.storm.hash-bang
  (:require [backtype.storm [clojure :refer [defbolt bolt emit-bolt! ack! topology spout-spec bolt-spec]] [config :refer [TOPOLOGY-DEBUG]]])
  (:import [backtype.storm LocalCluster LocalDRPC Constants]
           [backtype.storm.drpc PrepareRequest DRPCSpout JoinResult ReturnResults]
           [backtype.storm.coordination CoordinatedBolt CoordinatedBolt$SourceArgs]))



(defbolt add-bang ["id" "string"] [tuple collector]
  (let [s (.getString tuple 1)]
    (emit-bolt! collector [(.getValue tuple 0) (str "!"s)]))
  (ack! collector tuple))

(defbolt add-hash ["id" "string" "type"] [tuple collector]
  (let [s (.getString tuple 1)]
    (emit-bolt! collector [(.getValue tuple 0) (str "#"s) :hash]))
  (ack! collector tuple))

(defbolt add-tilde ["id" "string" "type"] [tuple collector]
  (let [s (.getString tuple 1)]
    (emit-bolt! collector [(.getValue tuple 0) (str "~"s) :tilde]))
  (ack! collector tuple))

(defbolt string-reducer ["id" "string"] {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [tuple]
              (let [{id "id" string "string" type "type"} tuple]
                (swap! scores #(assoc-in % [id type] string))
                (let [story-scores (get @scores id)
                      scored-types (set (keys story-scores))]
                  (when (= scored-types #{:hash :tilde})
                    (emit-bolt! collector [id (apply str (vals story-scores))])
                    (swap! scores #(dissoc % id)))))
              (ack! collector tuple)))))

(defn hash-bang-topology [drpc]
  (topology
   {"feed-build-requests" (spout-spec (DRPCSpout. "build-feed" drpc))}
   {"prepare-request" (bolt-spec {"feed-build-requests" :none}
                                 (PrepareRequest.))

    "add-bang" (bolt-spec {["prepare-request" PrepareRequest/ARGS_STREAM] :none}
                          (CoordinatedBolt. add-bang {} nil))

    "add-hash" (bolt-spec {"add-bang" :shuffle
                           ["add-bang" Constants/COORDINATED_STREAM_ID] :direct}
                          (CoordinatedBolt. add-hash {"add-bang" (CoordinatedBolt$SourceArgs/single)} nil))
    
    "join-result" (bolt-spec {["prepare-request" PrepareRequest/RETURN_STREAM] ["request"]
                              "add-hash" ["string"]} ;; must match last bolt, must be a 1-tuple
                             (JoinResult. "prepare-request"))

    "return-results" (bolt-spec {"join-result" :none}
                                (ReturnResults.))
    }))

(defn hash-bang-parallel-topology [drpc]
  (topology
   {"feed-build-requests" (spout-spec (DRPCSpout. "build-feed" drpc))}
   {"prepare-request" (bolt-spec {"feed-build-requests" :none}
                                 (PrepareRequest.))

    "add-bang" (bolt-spec {["prepare-request" PrepareRequest/ARGS_STREAM] :none}
                          (CoordinatedBolt. add-bang {} nil))

    "add-hash" (bolt-spec {"add-bang" :shuffle
                           ["add-bang" Constants/COORDINATED_STREAM_ID] :direct}
                          (CoordinatedBolt. add-hash {"add-bang" (CoordinatedBolt$SourceArgs/single)} nil))

    "add-tilde" (bolt-spec {"add-bang" :shuffle
                            ["add-bang" Constants/COORDINATED_STREAM_ID] :direct}
                           (CoordinatedBolt. add-tilde {"add-bang" (CoordinatedBolt$SourceArgs/single)} nil))

    "string-reducer" (bolt-spec
                      {"add-hash" ["id"]
                       ["add-hash" Constants/COORDINATED_STREAM_ID] :direct
                       "add-tilde" ["id"]
                       ["add-tilde" Constants/COORDINATED_STREAM_ID] :direct}
                      (CoordinatedBolt. string-reducer {"add-hash" (CoordinatedBolt$SourceArgs/all)
                                                        "add-tilde" (CoordinatedBolt$SourceArgs/all)} nil))
    
    "join-result" (bolt-spec {["prepare-request" PrepareRequest/RETURN_STREAM] ["request"]
                              "string-reducer" ["string"]} ;; must match last bolt, must be a 1-tuple
                             (JoinResult. "prepare-request"))

    "return-results" (bolt-spec {"join-result" :none}
                                (ReturnResults.))
    }))



(comment
  (def c (LocalCluster.))
  (def d (LocalDRPC.))
  (.submitTopology c "hash-bang" {TOPOLOGY-DEBUG true} (hash-bang-topology d))
  (.submitTopology c "hash-bang" {TOPOLOGY-DEBUG true} (hash-bang-parallel-topology d))
  (.execute d "build-feed" "hi")
  (.shutdown c)
  
  )


