(ns risingtide
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.jobs :as jobs]
            [risingtide.digesting-cache :as dc]
            ))

(def processor (atom nil))

(defn start-processor
  [config]
  {:processor (jobs/process-story-jobs-from-queue! (:connection config)
                                                   (:story-queue config))
   :expiration-thread (dc/cache-expiration-thread
                       (:cache-expiration-frequency config)
                       (:cache-ttl config))})

(defn -main []
  (let [config
        {:connection (redis/connection-map {})
         :story-queue "resque:queue:stories"
         :cache-expiration-frequency 60000
         :cache-ttl (* 1000 60 60 24)}]
    (log/info "Starting Rising Tide: processing story jobs with config" config)
    (swap! processor (fn [_] (start-processor config)))))

;;(-main)