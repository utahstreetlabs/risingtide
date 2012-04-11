(ns risingtide
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]
            [risingtide.feed :as feed]
            [risingtide.jobs :as jobs]
            [risingtide.digesting-cache :as dc]
            [risingtide.config :as config]
            [risingtide.web :as web]
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

(defn stop-expirer
  []
  (.shutdown (:expirer @processor)))

(defn wait-for-expirer
  []
  (.awaitTermination (:expirer @processor) 5 java.util.concurrent.TimeUnit/SECONDS))

(defn start-processor
  [config cache]
  (let [run-processor (atom true)]
    (log/info "preloading cache")
    (dc/preload! (:connections config) (:cache-ttl config))
    (log/info "cache preloaded with" (count @cache) "keys, starting processor")
    (merge config
           {:processor (future (jobs/process-story-jobs-from-queue!
                                run-processor
                                (:connections config)
                                (:story-queues config)))
            :run-processor run-processor
            :expirer (dc/cache-expirer
                                cache
                                (:cache-expiration-frequency config)
                                (:cache-ttl config))
            :cache cache})))

(defn stop
  "gracefully shut down the processor"
  []
  (log/info "stopping" processor)
  (stop-processor)
  (stop-expirer)
  (log/info "waiting for processor thread" (:processor @processor))
  (wait-for-processor)
  (log/info "waiting for expirer" (:expirer @processor))
  (wait-for-expirer)
  (log/info "stopped" processor)
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
         :cache-expiration-frequency (* 5 60) ;; seconds
         :cache-ttl (* 6 60 60) ;; seconds
         }]
    (log/info "Starting Rising Tide: processing story jobs with config" config)
    (swap! processor (fn [_] (start-processor config dc/story-cache)))
    "Started Rising Tide: The Feeds Must Flow"))

;;(-main)
;;(stop)
;;(stop-processor)
;;(stop-expirer)
