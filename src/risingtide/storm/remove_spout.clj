(ns risingtide.storm.remove-spout
  (:require
   [risingtide
    [config :as config]
    [redis :as redis]
    [resque :refer [args-from-resque pop-from-resque]]]
   [backtype.storm [clojure :refer [defspout spout emit-spout!]]]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [metrics.meters :refer [defmeter mark!]]))

(defspout remove-spout ["removal"]
  [conf context collector]
  (let [pool (redis/redis (:resque (config/redis-config)))]
    (spout
     (nextTuple
      []
      (when-let [resque-job (pop-from-resque pool "resque:queue:rising_tide_removes")]
        (if-let [removal (first (args-from-resque resque-job "Stories::RemoveListing"))]
          (let [id (str (java.util.UUID/randomUUID))]
            (log/info "processing removal "removal" with id "id)
            (emit-spout! collector [removal] :id id))
          (log/info "removal spout ignoring" resque-job))))
     (ack [id]
          (log/info "finished processing removal " id))
     (fail [id]
           (log/info "failed to process removal " id)))))
