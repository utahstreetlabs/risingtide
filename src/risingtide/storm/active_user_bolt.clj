(ns risingtide.storm.active-user-bolt
  (:require [risingtide
             [config :as config]]
            [risingtide.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack!]]]))

(def active-users-atom (atom []))

(defn active-users []
  @active-users-atom)


(defbolt active-user-bolt ["id" "user-ids" "story"] [{id "id" story "story" :as tuple} collector]
  (when (for-user-feed? story)
    (doseq [user-ids (partition-all config/active-user-bolt-batch-size (active-users))]
      (emit-bolt! collector [id user-ids story])))
  (ack! collector tuple))
