(ns risingtide.jobs
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [accession.core :as redis]
            [risingtide
             [interesting-story-cache :as interests]
             [feed :as feed]
             [resque :as resque]
             [stories :as stories]
             [key :as key]
             [digesting-cache :as dc]]))

(defn- add-interest-and-redigest!
  [redii type user-id object-id]
  ;; store and cache new interests
  (interests/add! redii user-id type object-id)
  ;; rebuild feeds
  (feed/redigest-user-feeds! redii (interests/feeds-to-update type user-id)))

(defn add-interest!
  [redii type [user-id object-id]]
  (bench (str "add interest in " type object-id " to " user-id)
         (add-interest-and-redigest! redii type user-id object-id)))

(defn add-interests!
  [redii type [user-ids object-id]]
  (bench (str "add interest in " type object-id " to " (count user-ids) " users ")
         (doseq [user-id user-ids]
           (add-interest-and-redigest! redii type user-id object-id))))

(defn remove-interest!
  [redii type [user-id object-id]]
  (bench (str "remove interest in " type object-id " to " user-id)
         (interests/remove! redii user-id type object-id)))

(defn add-story!
  [redii story]
  (bench (str "add story " story)
         (dc/add! redii story (now))
         (feed/redigest-user-feeds! redii (stories/interested-feeds redii story))
         (feed/redigest-everything-feed! redii)))

(defn- process-story-job!
  [redii json-message]
  (let [msg (json/read-json json-message)
        args (:args msg)]
    (case (:class msg)
      "Stories::AddInterestInListing" (add-interest! redii :listing args)
      "Stories::AddBatchInterestsInListing" (add-interests! redii :listing args)
      "Stories::AddInterestInActor" (add-interest! redii :actor args)
      "Stories::AddInterestInTag" (add-interest! redii :tag args)
      "Stories::RemoveInterestInListing" (remove-interest! redii :listing args)
      "Stories::RemoveInterestInActor" (remove-interest! redii :actor args)
      "Stories::RemoveInterestInTag" (remove-interest! redii :tag args)
      "Stories::Create" (add-story! redii (reduce merge args)))))

(defn process-story-jobs-from-queue!
  [run? redii queue-keys]
  ;; doseq is not lazy, and does not retain the head of the seq: perfect!
  (doseq [json-message (take-while identity (resque/jobs run? (:resque redii) queue-keys))]
    (try
      (process-story-job! redii json-message)
      (catch Exception e
        (log/error "failed to process job:" json-message "with" e)
        (safe-print-stack-trace e "jobs")))))

