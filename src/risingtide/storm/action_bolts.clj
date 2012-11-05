(ns risingtide.storm.action-bolts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [risingtide
             [core :refer [now]]]
            [risingtide.action.persist
             [solr :as solr]
             [aof :as aof]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]) )

(defn prepare-action [action]
  (assoc action
    :timestamp (now)))

(defbolt prepare-action-bolt ["action"] [{action "action" :as tuple} collector]
  (emit-bolt! collector [(prepare-action action)])
  (ack! collector tuple))

(defbolt save-action-bolt ["id" "action"] {:prepare true}
  [conf context collector]
  (let [syslog (aof/syslog)
        solr-conn (solr/connection)]
    (bolt
     (execute [{id "id" action "action" :as tuple}]
              (let [action-json (json/json-str action)]
                (aof/write! syslog action-json)
                (log/info "Saving action "action-json))
              (solr/save! solr-conn action)
              (emit-bolt! collector [id action])
              (ack! collector tuple)))))

