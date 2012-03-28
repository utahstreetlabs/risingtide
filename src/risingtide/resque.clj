(ns risingtide.resque
  "Utilities for reading from resque queues"
  (:use [robert.bruce :only [try-try-again]])
  (:require [clojure.tools.logging :as log]
            [accession.core :as redis]))

(defn first-value-from-queues
  [conn queue-names]
  (loop [queues queue-names]
    (if (empty? queues)
      nil
      (let [queue-name (first queues)]
       (let [v (redis/with-connection conn (redis/lpop queue-name))]
         (if v v (recur (next queues))))))))

(defn- pop-or-throw
  [run? conn queue-names]
  (let [queue-name (first queue-names)]
   (when @run?
     (log/trace "popping from" queue-name "on" conn)
     (let [v (first-value-from-queues conn queue-names)]
       (if v
         (do (log/info "Processing " v) v)
         (do (log/debug "didn't find anything in" queue-names "on" conn)
             (throw (Exception. (str "no value in " queue-names)))))))))

(defn- blocking-pop
  [run? conn queue-names]
  (try-try-again {:sleep 10 :decay #(min 2000 (* Math/E %)) :tries :unlimited}
                 pop-or-throw run? conn queue-names))

(defn jobs-seq
  [run? conn queue-names]
  (lazy-seq (cons (blocking-pop run? conn queue-names)
                  (jobs-seq run? conn queue-names))))

(defn jobs
  "a lazy seq that will read from a redis list

will block indefinitely when attempting to realize an element, polling
redis with exponential backoff until it can pop another element

will stop realizing new elements when the underlying polling seq returns nil"
  [run? conn queue-names]
  (take-while identity (jobs-seq run? conn queue-names)))

