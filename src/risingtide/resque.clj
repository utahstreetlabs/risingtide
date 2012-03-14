(ns risingtide.resque
  "Utilities for reading from resque queues"
  (:use [robert.bruce :only [try-try-again]])
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]))

(defn- pop-or-throw
  [conn queue-name]
  (log/trace "popping from" queue-name "on" conn)
  (let [v (redis/with-connection conn (redis/lpop queue-name))]
    (if v
      (do (log/info "Processing " v) v)
      (do (log/trace "didn't find anything in" queue-name "on" conn)
          (throw (Exception. (str "no value in " queue-name)))))))

(defn- blocking-pop
  [conn queue-name]
  (try-try-again {:sleep 10 :decay #(min 2000 (* Math/E %)) :tries :unlimited}
                 pop-or-throw conn queue-name))

(defn jobs
  "a lazy seq that will read from a redis list

will block indefinitely when attempting to realize an element, polling
redis with exponential backoff until it can pop another element"
  [conn queue-name]
  (lazy-seq (cons (blocking-pop conn queue-name) (jobs conn queue-name))))
