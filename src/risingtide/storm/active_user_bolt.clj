(ns risingtide.storm.active-user-bolt
  (:require [risingtide
             [core :refer [bench]]
             [config :as config]
             [redis :as redis]
             [active-users :refer [active-users]]]
            [risingtide.feed
             [filters :refer [for-user-feed?]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [timers :refer [deftimer time!]]
             [gauges :refer [gauge]]]))

(deftimer active-user-fetch-time)
(deftimer active-user-fanout-time)

(defbolt active-user-bolt ["id" "user-ids" "story"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)
        active-user-count (gauge "active-user-count" (active-users redii))]
    (bolt
     (execute [{id "id" story "story" :as tuple}]
              (when (for-user-feed? story)
                (time! active-user-fanout-time
                 (doseq [user-ids (partition-all config/active-user-bolt-batch-size
                                                 (time! active-user-fetch-time (active-users redii)))]
                   (emit-bolt! collector [id user-ids story] :anchor tuple))))
              (ack! collector tuple)))))
