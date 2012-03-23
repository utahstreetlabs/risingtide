(ns risingtide.jobs
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide
             [interests :as interests]
             [feed :as feed]
             [resque :as resque]
             [stories :as stories]
             [key :as key]
             [digesting-cache :as dc]]))

(defn add-interest!
  [conn type [user-id object-id]]
  (log/info "adding interest in" type object-id "to" user-id)
  (apply redis/with-connection conn
         (concat
          (interests/add-interest user-id (first-char type) object-id)
          (feed/redigest-user-feeds conn [(key/user-feed user-id "c") (key/user-feed user-id "n")])))
  (log/info "added interest in" type object-id "to" user-id))

(defn remove-interest!
  [conn type [user-id object-id]]
  (log/info "removing interest in" type object-id "to" user-id)
  (apply redis/with-connection conn
         (interests/remove-interest user-id (first-char type) object-id))
  (log/info "removed interest in" type object-id "to" user-id))

(defn add-story!
  [conn story]
  (let [time (now)
        user-feeds (stories/interested-feeds conn story)
        encoded-story (stories/encode story)]
    (log/info "adding" encoded-story)
    (dc/cache-story story time)
    (apply redis/with-connection conn
           (concat
            (map #(redis/zadd % time encoded-story) (stories/destination-story-sets story))
            (feed/redigest-user-feeds conn user-feeds)
            (feed/redigest-everything-feed)))
    (log/info "added" encoded-story)))

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
      "Stories::RemoveInterestInTag" (remove-interest! conn :tag args)
      "Stories::Create" (add-story! conn (first args)))))

(defn process-story-jobs-from-queue!
  [run? connection-specs queue-key]
  (let [resque-conn (:resque connection-specs)
        feed-conn (:feeds connection-specs)]
   ;; doseq is not lazy, and does not retain the head of the seq: perfect!
   (doseq [json-message (take-while identity (resque/jobs run? resque-conn queue-key))]
     (try
       (process-story-job! feed-conn json-message)
       (catch Exception e
         (log/error "failed to process job:" json-message "with" e)
         (safe-print-stack-trace e "jobs"))))))

