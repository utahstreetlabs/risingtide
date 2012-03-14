(ns risingtide.jobs
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide
             [interests :as interests]
             [feed :as feed]
             [resque :as resque]]))

(defn- add-interest!
  [conn type [user-id object-id]]
  (redis/with-connection conn
    (interests/add-interest user-id (first-char type) object-id)
    (feed/build-feed :cards user-id (feed/interesting-keys conn :cards user-id))
    (feed/build-feed :notifications user-id (feed/interesting-keys conn :notifications user-id))))

(defn- remove-interest!
  [conn type [user-id object-id]]
  (redis/with-connection conn
    (interests/remove-interest user-id (first-char type) object-id)))

(defn- process-story-job!
  [conn json-message]
  (let [msg (json/read-json json-message)
        args (:args msg)]
    (case (:class msg)
      "Stories::AddInterestInListing" (add-interest! conn :listing args)
      "Stories::AddInterestInActor" (add-interest! conn :actor args)
      "Stories::AddInterestInTag" (add-interest! conn :tag args)
      "Stories::RemoveInterestInListing" (remove-interest! conn :listing args)
      "Stories::RemoveInterestInActor" (remove-interest! conn :actor args)
      "Stories::RemoveInterestInTag" (remove-interest! conn :tag args))))

(defn process-story-jobs-from-queue!
  [conn queue-key]
  ;; doseq is not lazy, and does not retain the head of the seq: perfect!
  (doseq [json-message (resque/jobs conn queue-key)]
    (try
      (process-story-job! conn json-message)
      (catch Exception e
        (log/error "failed to process job:" json-message "with" e)))))
