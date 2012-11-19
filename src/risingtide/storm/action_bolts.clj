(ns risingtide.storm.action-bolts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [risingtide
             [core :refer [now]]]
            [risingtide.action.persist
             [solr :as solr]
             [aof :as aof]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [metrics.meters :refer [defmeter mark!]]))

(defmeter action-saved "actions saved")

(defn prepare-action [action]
  (assoc action
    :timestamp (now)))

(defbolt prepare-action-bolt ["action"] [{action "action" :as tuple} collector]
  (emit-bolt! collector [(prepare-action action)] :anchor tuple)
  (ack! collector tuple))

(defbolt save-action-bolt ["id" "action"] {:prepare true}
  [conf context collector]
  (let [syslog (aof/syslog)
        solr-conn (solr/connection)]
    (bolt
     (execute [{id "id" action "action" :as tuple}]
              (aof/write! syslog (json/json-str action))
              (solr/save! solr-conn action)
              (emit-bolt! collector [id action] :anchor tuple)
              (mark! action-saved)
              (ack! collector tuple)))))

