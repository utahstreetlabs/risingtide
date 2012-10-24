(ns risingtide.storm.active-user-bolt
  (:require [risingtide.v2.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack!]]]))

(defn active-users []
  [47])

(defbolt active-user-bolt ["user-id" "story"] [tuple collector]
  (let [{story "story"} tuple]
    (when (for-user-feed? story)
     (doseq [user-id (active-users)]
       (emit-bolt! collector [user-id story]))))
  (ack! collector tuple))
