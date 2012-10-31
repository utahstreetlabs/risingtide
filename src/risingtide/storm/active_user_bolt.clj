(ns risingtide.storm.active-user-bolt
  (:require [risingtide.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack!]]]))

(def active-users-atom (atom []))

(defn active-users []
  @active-users-atom)


(defbolt active-user-bolt ["id" "user-id" "story"] [{id "id" story "story" :as tuple} collector]
  (when (for-user-feed? story)
    (doseq [user-id (active-users)]
      (emit-bolt! collector [id user-id story])))
  (ack! collector tuple))
