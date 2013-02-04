(ns risingtide.integration.support.storm
  "Utilities for testing storm topologies.

Some of these hese were introduced here:

http://www.pixelmachine.org/2011/12/17/Testing-Storm-Topologies.html

and are included in storm-test:

https://github.com/schleyfox/storm-test

But that library is not compatible with 0.8.1. Don't have time to dig
into updating it right now, so just reproduce them here.

This commit:

https://github.com/schleyfox/storm-test/pull/1

claims to fix this issue, but it has not yet been reviewed/merged."
  (:require [clojure.contrib.logging :refer :all]
            [backtype.storm
             [config :refer :all]
             [testing :refer
              [with-local-cluster with-simulated-time-local-cluster ms= read-tuples]
              :as storm]]
            [risingtide.storm
             [core :refer [standard-topology-config]]])
  (:import [org.apache.log4j Logger]))

(defn set-log-level
  "From http://www.pixelmachine.org/2011/12/17/Testing-Storm-Topologies.html"
  [level]
  (.. (Logger/getLogger
       "org.apache.zookeeper.server.NIOServerCnxn")
      (setLevel level))
  (.. (impl-get-log "") getLogger getParent
      (setLevel level)))

(defmacro with-quiet-logs
  "From http://www.pixelmachine.org/2011/12/17/Testing-Storm-Topologies.html"
  [& body]
  `(let [old-level# (.. (impl-get-log "") getLogger
                        getParent getLevel) ]
     (set-log-level org.apache.log4j.Level/OFF)
     (let [ ret# (do ~@body) ]
       (set-log-level old-level#)
       ret#)))

(defn complete-topology [topology sources]
  (with-quiet-logs
   (with-local-cluster [cluster :daemon-conf (merge standard-topology-config)]
     (let [results
           (storm/complete-topology cluster topology
                                    :mock-sources sources
                                    :storm-conf {TOPOLOGY-WORKERS 6})]
       ;; There's a weird bug with cluster cleanup that can be worked around with a big fat sleep:
       ;; https://github.com/nathanmarz/storm/issues/356
       (Thread/sleep 5000)
       results))))