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

(defn add-interest!
  [redii type [user-id object-id]]
  (log/info "adding interest in" type object-id "to" user-id)
  (let [feeds-to-update (interests/feeds-to-update type user-id)]
    (doseq [feed feeds-to-update]
      (when (= 0 (redis/with-connection (:feeds redii) (redis/zcard feed)))
        (log/info "bootstrapping" feed)
        (let [[feed-type user-id] (key/type-user-id-from-feed-key feed)]
          (apply redis/with-connection (:feeds redii) (feed/build-and-truncate-feed redii user-id feed-type)))))

    ;; store and cache new interests
    (interests/add! redii user-id type object-id)

    ;; use cached information to rebuild feeds
    (apply redis/with-connection (:feeds redii)
           (feed/redigest-user-feeds redii feeds-to-update)))
  (log/info "added interest in" type object-id "to" user-id))

(defn add-interests!
  [redii type [user-ids object-id]]
  (doseq [user-id user-ids]
    (add-interest! redii type [user-id object-id])))

(defn remove-interest!
  [redii type [user-id object-id]]
  (log/info "removing interest in" type object-id "to" user-id)
  (interests/remove! redii user-id type object-id)
  (log/info "removed interest in" type object-id "to" user-id))

(defn add-story!
  [redii story]
  (let [time (now)
        user-feeds (stories/interested-feeds redii story)
        encoded-story (stories/encode story)]
    (log/info "adding" story)
    (dc/cache-story story time)
    (log/debug "cached" encoded-story)
    (bench "redigesting"
           (apply redis/with-connection (:feeds redii)
                  (concat
                   (bench "add story queries" (doall (map #(redis/zadd % time encoded-story) (stories/destination-story-sets story))))
                   (bench "user feed queries" (doall (feed/redigest-user-feeds (:feeds redii) user-feeds)))
                   (bench "everything feed queries" (doall (feed/redigest-everything-feed))))))
    (log/info "added" encoded-story)))

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
      "Stories::Create" (add-story! (:feeds redii) (reduce merge args)))))

(defn process-story-jobs-from-queue!
  [run? redii queue-keys]
  (let [resque-conn (:resque redii)
        feed-conn (:feeds redii)]
   ;; doseq is not lazy, and does not retain the head of the seq: perfect!
   (doseq [json-message (take-while identity (resque/jobs run? resque-conn queue-keys))]
     (try
       (process-story-job! redii json-message)
       (catch Exception e
         (log/error "failed to process job:" json-message "with" e)
         (safe-print-stack-trace e "jobs"))))))

