(ns risingtide.storm.drpc
  (require [backtype.storm [clojure :as clj]])
  (:import [backtype.storm Constants]
           [backtype.storm.drpc PrepareRequest DRPCSpout JoinResult ReturnResults]
           [backtype.storm.coordination CoordinatedBolt CoordinatedBolt$SourceArgs]))

(def prepare-request-id "prepare-request")

(defn- bolt-direct-streams [input-names]
  (reduce (fn [h name] (assoc h [name Constants/COORDINATED_STREAM_ID] :direct))
          {} input-names))

(defn- source-bolts [inputs]
  (map #(if (vector? %) (first %) %) (keys inputs)))

(defn- bolt-inputs [inputs]
  (if inputs
    (merge inputs (bolt-direct-streams (source-bolts inputs)))
    {[prepare-request-id PrepareRequest/ARGS_STREAM] :none}))

(defn- bolt-sources [inputs source-type]
  (reduce (fn [h name] (assoc h name source-type))
          {} (source-bolts inputs)))

(defn bolt-spec [inputs bolt source-type & kwargs]
  (apply clj/bolt-spec
         (merge inputs (bolt-inputs inputs))
         (CoordinatedBolt. bolt (bolt-sources inputs source-type) nil)
         kwargs))

(defn- wrap-bolts [bolts source]
  (reduce (fn [h [name [inputs bolt & args]]] (assoc h name (apply bolt-spec inputs bolt source args)))
          {} bolts))

(defn topology-spouts [drpc method-name spout-name]
  (let [spout (if drpc
                (DRPCSpout. method-name drpc)
                (DRPCSpout. method-name))]
   {spout-name (clj/spout-spec spout)}))

(defn topology-bolts [spout-name
                      [first-bolt-name first-bolt]
                      second-bolts
                      remaining-bolts
                      [last-bolt-name last-bolt-field]]
  (let [join-bolt-name (str spout-name "-join-results")]
   (merge
    {prepare-request-id (clj/bolt-spec {spout-name :none} (PrepareRequest.))}
    {first-bolt-name (bolt-spec nil first-bolt nil)}
    (wrap-bolts second-bolts (CoordinatedBolt$SourceArgs/single))
    (wrap-bolts remaining-bolts (CoordinatedBolt$SourceArgs/all))
    {join-bolt-name (clj/bolt-spec {[prepare-request-id PrepareRequest/RETURN_STREAM] ["request"]
                                   last-bolt-name [last-bolt-field]} ;; must match last bolt, must be a 1-tuple
                                  (JoinResult. prepare-request-id))

     (str spout-name "-return-results") (clj/bolt-spec {join-bolt-name :none}
                                                       (ReturnResults.))})))