(ns risingtide.storm.action-spout
  (:require
   [risingtide
    [config :as config]
    [redis :as redis]]
   [backtype.storm [clojure :refer [defspout spout emit-spout!]]]
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]))

(defn action-from-resque [action]
  (let [json (json/read-json action)]
    (when (= "Stories::Create" (:class json))
      (first (:args json)))))

(defspout resque-spout ["action"]
  [conf context collector]
  (let [pool (redis/redis (config/redis-config))]
   (spout
    (nextTuple []
     (when-let [string (let [r (.getResource pool)]
                         (try
                           (.lpop r "resque:queue:rising_tide_stories")
                           (finally (.returnResource pool r))))]
       (if-let [action (action-from-resque string)]
         (emit-spout! collector [action])
         (log/info "action spout ignoring" string))))
    (ack [id]
         ;; You only need to define this method for reliable spouts
         ;; (such as one that reads off of a queue like Kestrel)
         ;; This is an unreliable spout, so it does nothing here
         ))))
