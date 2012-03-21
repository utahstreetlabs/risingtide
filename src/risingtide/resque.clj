(ns risingtide.resque
  "Utilities for reading from resque queues"
  (:use [robert.bruce :only [try-try-again]])
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]))

(defn- pop-or-throw
  [run? conn queue-name]
  (when @run?
   (log/trace "popping from" queue-name "on" conn)
   (let [v (redis/with-connection conn (redis/lpop queue-name))]
     (if v
       (do (log/info "Processing " v) v)
       (do (log/debug "didn't find anything in" queue-name "on" conn)
           (throw (Exception. (str "no value in " queue-name))))))))

(defn- blocking-pop
  [run? conn queue-name]
  (try-try-again {:sleep 10 :decay #(min 2000 (* Math/E %)) :tries :unlimited}
                 pop-or-throw run? conn queue-name))

(defn jobs-seq
  [run? conn queue-name]
  (lazy-seq (cons (blocking-pop run? conn queue-name)
                  (jobs-seq run? conn queue-name))))

(defn jobs
  "a lazy seq that will read from a redis list

will block indefinitely when attempting to realize an element, polling
redis with exponential backoff until it can pop another element

will stop realizing new elements when the underlying polling seq returns nil"
  [run? conn queue-name]
  (take-while identity (jobs-seq run? conn queue-name)))

