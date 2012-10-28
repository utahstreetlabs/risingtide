(ns risingtide.storm.story-bolts
  (:require [risingtide
             [core :refer [now]]
             [config :as config]]
            [risingtide.story.persist.solr :as solr]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]])
  (:import org.productivity.java.syslog4j.Syslog))

(defn prepare-story [story]
  (assoc story
    :timestamp (now)
    :id (or (:id story) (str (java.util.UUID/randomUUID)))))

(defbolt prepare-story-bolt ["story"] [{story "story" :as tuple} collector]
  (emit-bolt! collector [(prepare-story story)])
  (ack! collector tuple))

(defbolt write-story ["feed"] {:prepare true}
  [conf context collector]
  (let [syslog (doto (Syslog/getInstance "tcp")
                 (-> (.getConfig) (.setHost (:host (config/story-bolt-syslog))))
                 (-> (.getConfig) (.setPort (:port (config/story-bolt-syslog)))))
        solr-conn (solr/connection)]
    (bolt
     (execute [{story "story" :as tuple}]
              (.info syslog story)
              (solr/save! solr-conn story)
              (ack! collector tuple)))))

