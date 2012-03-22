(ns risingtide
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.feed :as feed]
            [risingtide.jobs :as jobs]
            [risingtide.digesting-cache :as dc]
            [risingtide.config :as config]
            [risingtide.web :as web]
            [clj-logging-config.log4j :as log-config])
  (:import [sun.misc Signal SignalHandler]))

(def processor (atom nil))

(defn stop-thread
  [name]
  (swap! (@processor name) (constantly false)))

(defn stop-processor
  []
  (stop-thread :run-processor))

(defn stop-expiration-thread
  []
  (stop-thread :run-expiration-thread))

(defn start-processor
  [config cache]
  (let [run-processor (atom true)
        run-expiration-thread (atom true)]
    (feed/preload-digest-cache! (redis/connection-map (:feeds (:connections config)))
                                (:cache-ttl config))
    (merge config
           {:processor (future (jobs/process-story-jobs-from-queue!
                                run-processor
                                (:connections config)
                                (:story-queue config)))
            :run-processor run-processor
            :expiration-thread (dc/cache-expiration-thread
                                run-expiration-thread
                                cache
                                (:cache-expiration-frequency config)
                                (:cache-ttl config))
            :run-expiration-thread run-expiration-thread
            :cache cache})))

(defn stop
  "gracefully shut down the processor"
  []
  (log/info "stopping" processor)
  (stop-processor)
  (stop-expiration-thread)
  (log/info "waiting for processor thread" (:processor @processor))
  (deref (:processor @processor))
  (log/info "waiting for expiration thread" (:expiration-thread @processor))
  (deref (:expiration-thread @processor))
  (log/info "stopped" processor)
  @processor)

;; Logging ;;

(defn setup-loggers [loggers]
  ;; configure the logger
  (apply log-config/set-loggers! loggers))

;; Signal Handling ;;

(def graceful-stop-handler
  (proxy [SignalHandler] []
    (handle [signal]
      (log/info "received" signal)
      (try (stop)
           (catch Throwable t (log/error "error stopping:" t) (safe-print-stack-trace t "shutdown")))
      (log/info "stopped")
      (shutdown-agents)
      (.exit (Runtime/getRuntime) 0))))

(defn install-signal-handlers
  []
  (let []
    (Signal/handle (Signal. "INT") graceful-stop-handler)
    (Signal/handle (Signal. "TERM") graceful-stop-handler)))

;; This is where the magic happens ;;

(defn -main []
  (setup-loggers (config/loggers (env)))
  (install-signal-handlers)
  (web/run! processor)
  (let [config
        {:connections (config/redis (env))
         :story-queue "resque:queue:stories"
         :cache-expiration-frequency 60000
         :cache-ttl (* 1000 60 60 24)}]
    (log/info "Starting Rising Tide: processing story jobs with config" config)
    (swap! processor (fn [_] (start-processor config dc/story-cache)))
    "Started Rising Tide: The Feeds Must Flow"))

;;(-main)
;;(stop)
;;(stop-processor)
;;(stop-expiration-thread)
