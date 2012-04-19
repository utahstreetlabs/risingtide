(ns risingtide
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.feed :as feed]
            [risingtide.jobs :as jobs]
            [risingtide.config :as config]
            [risingtide.web :as web]
            [risingtide.digest :as digest]
            [clj-logging-config.log4j :as log-config]
            [mycroft.main :as mycroft])
  (:import [sun.misc Signal SignalHandler]))

(def processor (atom nil))

(defn stop-thread
  [name]
  (swap! (@processor name) (constantly false)))

(defn stop-processor
  []
  (stop-thread :run-processor))

(defn wait-for-processor
  []
  (deref (:processor @processor)))

(defn stop-flusher
  []
  (.shutdown (:flusher @processor)))

(defn wait-for-flusher
  []
  (.awaitTermination (:flusher @processor) 5 java.util.concurrent.TimeUnit/SECONDS))

(defn start-processor
  [config cache]
  (let [run-processor (atom true)]
    (merge config
           {:processor (future (jobs/process-story-jobs-from-queue!
                                run-processor
                                (:connections config)
                                (:story-queues config)))
            :run-processor run-processor
            :flusher (digest/cache-flusher cache (:connections config) (:cache-flush-frequency config))
            :cache cache})))

(defn stop
  "gracefully shut down the processor"
  []
  (log/info "stopping processor")
  (stop-processor)
  (stop-flusher)
  (log/info "waiting for processor thread" (:processor @processor))
  (wait-for-processor)
  (log/info "waiting for flusher" (:flusher @processor))
  (wait-for-flusher)
  (digest/write-cache! (:cache @processor) (:connections @processor))
  (log/info "stopped processor")
  @processor)

;; Signal Handling ;;

(def graceful-stop-handler
  (proxy [SignalHandler] []
    (handle [signal]
      (log/info "received" signal)
      (try (stop)
           (catch Throwable t (log/error "error stopping:" t) (safe-print-stack-trace t)))
      (log/info "stopped")
      (shutdown-agents)
      (.exit (Runtime/getRuntime) 0))))

(defn install-signal-handlers
  []
  (let []
    (Signal/handle (Signal. "INT") graceful-stop-handler)
    (Signal/handle (Signal. "TERM") graceful-stop-handler)))

(defn- connections
  []
  (reduce (fn [m [key val]] (assoc m key (redis/connection-map val))) {}
          (config/redis env)))

;; This is where the magic happens ;;

(defn -main []
  (log-config/set-logger! :level :info :out :console)
  (install-signal-handlers)
  (web/run! processor)
  ;; mycroft is a var inspector. it makes it easy to see wtf is going
  ;; on inside risingtide.
  (mycroft/run (config/ports :mycroft))
  (let [config
        {:connections (connections)
         :story-queues ["resque:queue:rising_tide_priority"
                        "resque:queue:rising_tide_stories"]
         :cache-flush-frequency 5  ;; seconds
         :cache-ttl (* 6 60 60) ;; seconds
         }]
    (log/info "Starting Rising Tide: processing story jobs with config" config)
    (swap! processor (fn [_] (start-processor config digest/feed-cache)))
    "Started Rising Tide: The Feeds Must Flow"))

;;(-main)
;;(stop)
;;(stop-processor)
;;(stop-flusher)
