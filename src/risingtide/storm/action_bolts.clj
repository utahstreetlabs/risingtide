(ns risingtide.storm.action-bolts
  (:require [clojure.data.json :as json]
            [risingtide
             [core :refer [now]]
             [config :as config]]
            [risingtide.action.persist.solr :as solr]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]])
  (:import org.productivity.java.syslog4j.Syslog))

(defn prepare-action [action]
  (assoc action
    :timestamp (now)))

(defbolt prepare-action-bolt ["action"] [{action "action" :as tuple} collector]
  (emit-bolt! collector [(prepare-action action)])
  (ack! collector tuple))

(defbolt save-action-bolt ["id" "action"] {:prepare true}
  [conf context collector]
  (let [syslog (doto (Syslog/getInstance "tcp")
                 (-> (.getConfig) (.setHost (:host (config/action-bolt-syslog))))
                 (-> (.getConfig) (.setPort (:port (config/action-bolt-syslog)))))
        solr-conn (solr/connection)]
    (bolt
     (execute [{id "id" action "action" :as tuple}]
              (.info syslog (json/json-str action))
              (solr/save! solr-conn action)
              (emit-bolt! collector [id action])
              (ack! collector tuple)))))

