(ns risingtide.jobs
  (:use [robert.bruce :only [try-try-again]])
  (:require [accession.core :as redis]
            [clojure.tools.logging :as log]
            [risingtide.interests :as interests]
            [clojure.data.json :as json]))

(defn pop-or-throw
  [conn key]
  (let [v (redis/with-connection conn (redis/lpop key))]
    (if v
      (do (log/info "Processing " v) v)
      (throw (Exception. (str "no value in " key))))))

(defn blocking-pop
  [conn key]
  (try-try-again {:sleep 10 :decay #(min 2000 (* Math/E %)) :tries :unlimited}
                 pop-or-throw conn key))

(defn redis-seq
  "a lazy seq that will read from a redis list

will block indefinitely when attempting to realize an element, polling
redis with exponential backoff until it can pop another element"
  [conn key]
  (lazy-seq (cons (blocking-pop conn key) (redis-seq conn key))))

(defn process-story-job
  [conn json-message]
  (let [msg (json/read-json json-message)]
    (redis/with-connection conn
     (apply
      (case (:class msg)
        "Stories::AddInterestInListing" interests/add-interest-in-listing
        "Stories::AddInterestInActor" interests/add-interest-in-actor
        "Stories::AddInterestInTag" interests/add-interest-in-tag
        "Stories::RemoveInterestInListing" interests/remove-interest-in-listing
        "Stories::RemoveInterestInActor" interests/remove-interest-in-actor
        "Stories::RemoveInterestInTag" interests/remove-interest-in-tag)
      (:args msg)))))

(defn process-story-jobs-from-queue!
  [conn queue-key]
  (map #(process-story-job conn %)
       (redis-seq conn queue-key)))


(comment
  (def c (redis/connection-map {}))
  (process-story-jobs-from-queue! c "resque:queue:stories")
 )

