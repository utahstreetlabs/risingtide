(ns risingtide.storm.FeedTopology
  (:require [risingtide.storm.core :refer [feed-generation-topology]]
            [backtype.storm
             [config :refer :all]])
  (:import [backtype.storm StormSubmitter])
  (:gen-class))

(defn -main [& {debug "debug" workers "workers" :or {debug "false" workers "4"}}]
  (StormSubmitter/submitTopology
   "feed topology"
   {TOPOLOGY-DEBUG (Boolean/parseBoolean debug)
    TOPOLOGY-WORKERS (Integer/parseInt workers)
    TOPOLOGY-MAX-SPOUT-PENDING 1
    TOPOLOGY-MESSAGE-TIMEOUT-SECS 60}
   (feed-generation-topology)))
