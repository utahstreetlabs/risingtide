(ns risingtide
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.jobs :as jobs]))

(defn -main []
  (let [con (redis/connection-map {})
        queue-key "resque:queue:stories"]
    (log/info "Starting Rising Tide: processing story jobs from" queue-key "on Redis instance at" con)
    (jobs/process-story-jobs-from-queue! con queue-key)))

;;(-main)