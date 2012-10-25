(ns risingtide.storm.batch-feed-builder
  (:import
   [backtype.storm.topology.base BaseBatchBolt]))

(defn batch-feed-builder []
  (let [id (atom nil)
        collector (atom nil)]
    (proxy [BaseBatchBolt] []
      (prepare [conf context collector id]
        (swap! id (constantly id))
        (swap! id (constantly collector)))
      (execute [tuple]
        )
      (finishBatch [])
      (declareOutputFields [declarer]
        (.declare declarer)))))