(ns risingtide.storm.local
  (:require [risingtide.config :as config]
            [risingtide.storm.core :refer [standard-topology-config feed-generation-topology]]
            [risingtide.storm.drpc.local-server :as local-drpc-server]
            [backtype.storm
             [clojure :refer [defbolt bolt emit-bolt! ack! topology spout-spec bolt-spec]]
             [config :refer :all]
             [testing :refer [mk-local-storm-cluster]]]
            [metrics.core :refer [report-to-console]])
  (:import [backtype.storm LocalCluster LocalDRPC]))

(defn run! [& {debug "debug" workers "workers"
               report-local-stats "report-local-stats"
               :or {debug "false" workers "1" report-local-stats "false"}}]

  (let [drpc (LocalDRPC.)]
    (doto (LocalCluster. (mk-local-storm-cluster
                          :supervisors 1
                          :ports-per-supervisor 1
                          :daemon-conf
                          {TOPOLOGY-DEBUG (Boolean/parseBoolean debug)
                           TOPOLOGY-WORKERS (Integer/parseInt workers)
                           TOPOLOGY-SLEEP-SPOUT-WAIT-STRATEGY-TIME-MS 1000
                           TOPOLOGY-TASKS 1
                           TOPOLOGY-WORKER-SHARED-THREAD-POOL-SIZE 1
                           TOPOLOGY-ENABLE-MESSAGE-TIMEOUTS true
                           WORKER-HEARTBEAT-FREQUENCY-SECS 20
                           TASK-HEARTBEAT-FREQUENCY-SECS 20
                           SUPERVISOR-HEARTBEAT-FREQUENCY-SECS 20
                           NIMBUS-MONITOR-FREQ-SECS 20
                           TASK-REFRESH-POLL-SECS 20
                           SUPERVISOR-MONITOR-FREQUENCY-SECS 20}))
      (.submitTopology "story" standard-topology-config
                      (feed-generation-topology drpc)))
    (local-drpc-server/run! drpc (config/local-drpc-port))
    (when (Boolean/parseBoolean report-local-stats)
      (report-to-console 10))))

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