(ns risingtide.storm.active-user-bolt
  (:require [risingtide
             [core :refer [bench]]
             [config :as config]
             [redis :as redis]
             [active-users :refer [active-users]]]
            [risingtide.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]))

(defbolt active-user-bolt ["id" "user-ids" "story"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)]
    (bolt
     (execute [{id "id" story "story" :as tuple}]
              (when (for-user-feed? story)
                (doseq [user-ids (partition-all config/active-user-bolt-batch-size
                                                (bench "finding active users" (active-users redii)))]
                  (emit-bolt! collector [id user-ids story] :anchor tuple)))
              (ack! collector tuple)))))
