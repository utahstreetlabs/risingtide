(ns risingtide.storm.action-bolts
  (:require [clojure.data.json :as json]
            [risingtide
             [core :refer [now]]
             [config :as config]]
            [risingtide.story.persist.solr :as solr]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]])
  (:import org.productivity.java.syslog4j.Syslog))

(defn prepare-action [story]
  (assoc story
    :timestamp (now)))

(defbolt prepare-action-bolt ["action"] [{action "action" :as tuple} collector]
  (emit-bolt! collector [(prepare-action action)])
  (ack! collector tuple))

(defbolt save-action-bolt ["feed"] {:prepare true}
  [conf context collector]
  (let [syslog (doto (Syslog/getInstance "tcp")
                 (-> (.getConfig) (.setHost (:host (config/story-bolt-syslog))))
                 (-> (.getConfig) (.setPort (:port (config/story-bolt-syslog)))))
        solr-conn (solr/connection)]
    (bolt
     (execute [{action "action" :as tuple}]
              (.info syslog (json/json-str action))
              (solr/save! solr-conn action)
              (ack! collector tuple)))))

