(ns risingtide.storm.active-user-bolt
  (:require [risingtide
             [config :as config]
             [redis :as redis]
             [active-users :refer [active-users]]]
            [risingtide.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defbolt active-user-bolt ["id" "user-ids" "story"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)]
    (bolt
     (execute [{id "id" story "story" :as tuple}]
              (prn "ACTIVES!"  (active-users redii))
              (when (for-user-feed? story)
                (doseq [user-ids (partition-all config/active-user-bolt-batch-size (active-users redii))]
                  (emit-bolt! collector [id user-ids story])))
              (ack! collector tuple)))))
