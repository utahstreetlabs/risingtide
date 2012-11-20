(ns risingtide.storm.action-spout
  (:require
   [risingtide
    [config :as config]
    [redis :as redis]]
   [backtype.storm [clojure :refer [defspout spout emit-spout!]]]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [metrics.meters :refer [defmeter mark!]]))

(defn action-from-resque [action]
  (let [json (json/read-json action)]
    (when (= "Stories::Create" (:class json))
      (first (:args json)))))

(defmeter action-created "actions created")
(defmeter action-processed "actions processed")
(defmeter action-processing-failed "actions processing attempts failed")

(defspout resque-spout ["action"]
  [conf context collector]
  (let [pool (redis/redis (:resque (config/redis-config)))]
   (spout
    (nextTuple []
     (when-let [string (let [r (.getResource pool)]
                         (try
                           (.lpop r "resque:queue:rising_tide_actions")
                           (finally (.returnResource pool r))))]
       (if-let [action (action-from-resque string)]
         (let [id (str (java.util.UUID/randomUUID))]
           (log/info "processing action "action" with id "id)
           (mark! action-created)
           ;; disable reliability for now by removing ":id id"
           ;; add this back whenever we want to make things more bulletproof
           (emit-spout! collector [action]))
         (log/info "action spout ignoring" string))))
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
