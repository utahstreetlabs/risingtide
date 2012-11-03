(ns risingtide.storm.FeedTopology
  (:require [risingtide.storm.core :refer [feed-generation-topology]]
            [backtype.storm
             [config :refer :all]])
  (:import [backtype.storm StormSubmitter])
  (:gen-class))

(defn -main []
  (StormSubmitter/submitTopology
   "feed topology"
   {TOPOLOGY-DEBUG true}
   (feed-generation-topology)))