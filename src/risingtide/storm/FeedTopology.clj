(ns risingtide.storm.FeedTopology
  (:require [risingtide.storm.core :refer [feed-generation-topology standard-topology-config]]
            [backtype.storm
             [config :refer :all]])
  (:import [backtype.storm StormSubmitter])
  (:gen-class))

(defn -main [& {debug "debug" workers "workers" :or {debug "false" workers "4"}}]
  (StormSubmitter/submitTopology
   "feed topology"
   (merge
    standard-topology-config
    {TOPOLOGY-DEBUG (Boolean/parseBoolean debug)
     TOPOLOGY-WORKERS (Integer/parseInt workers)})
   (feed-generation-topology)))
