(ns risingtide.storm.action-spout
  (:require
   [risingtide
    [config :as config]
    [redis :as redis]
    [resque :refer [args-from-resque pop-from-resque]]]
   [backtype.storm [clojure :refer [defspout spout emit-spout!]]]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [metrics.meters :refer [defmeter mark!]]))

(defmeter action-created "actions created")
(defmeter action-processed "actions processed")
(defmeter action-processing-failed "actions processing attempts failed")

(defspout action-spout ["action"]
  [conf context collector]
  (let [pool (redis/redis (:resque (config/redis-config)))]
    (spout
     (nextTuple
      []
      (when-let [resque-job (pop-from-resque pool "resque:queue:rising_tide_actions")]
        (if-let [action (first (args-from-resque resque-job "Stories::Create"))]
          (let [id (str (java.util.UUID/randomUUID))]
            (log/info "processing action "action" with id "id)
            (mark! action-created)
            (emit-spout! collector [action] :id id))
          (log/info "action spout ignoring" resque-job))))
     (ack [id]
          (log/info "finished processing action " id)
          (mark! action-processed)
          ;; You only need to define this method for reliable spouts
          ;; (such as one that reads off of a queue like Kestrel)
          ;; This is an unreliable spout, so it does nothing here
          )
     (fail [id]
           (log/info "failed to process action " id)
           (mark! action-processing-failed)
           ;; You only need to define this method for reliable spouts
           ;; (such as one that reads off of a queue like Kestrel)
           ;; This is an unreliable spout, so it does nothing here
           ))))
