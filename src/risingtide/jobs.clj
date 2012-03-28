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
             [digesting-cache :as dc]
             [interesting-story-cache :as isc]]))

(defn add-interest!
  [conn type [user-id object-id]]
  (log/info "adding interest in" type object-id "to" user-id)
  (let [feeds-to-update (isc/feeds-to-update type user-id)]
    (isc/add-interest-to-feeds! isc/interesting-story-cache
                                (interests/interest-token (first-char type) object-id)
                                feeds-to-update)
    (doseq [feed feeds-to-update]
      (when (= 0 (redis/with-connection conn (redis/zcard feed)))
        (log/info "bootstrapping" feed)
        (let [[feed-type user-id] (key/type-user-id-from-feed-key feed)]
         (apply redis/with-connection conn (feed/build-and-truncate-feed conn user-id feed-type)))))
    (apply redis/with-connection conn
           (concat
            (interests/add-interest user-id (first-char type) object-id)
            (feed/redigest-user-feeds conn feeds-to-update))))
  (log/info "added interest in" type object-id "to" user-id))

(defn add-interests!
  [conn type [user-ids object-id]]
  (doseq [user-id user-ids]
    (add-interest! conn type [user-id object-id])))

(defn remove-interest!
  [conn type [user-id object-id]]
  (log/info "removing interest in" type object-id "to" user-id)
  (isc/remove-interest-from-feeds! isc/interesting-story-cache
                                   (interests/interest-token (first-char type) object-id)
                                   (isc/feeds-to-update type user-id))
  (apply redis/with-connection conn
         (interests/remove-interest user-id (first-char type) object-id))
  (log/info "removed interest in" type object-id "to" user-id))

(defn add-story!
  [conn story]
  (let [time (now)
        user-feeds (stories/interested-feeds conn story)
        encoded-story (stories/encode story)]
    (log/info "adding" story)
    (dc/cache-story story time)
    (log/debug "cached" encoded-story)
    (bench "redigesting"
           (apply redis/with-connection conn
                  (concat
                   (bench "add story queries" (doall (map #(redis/zadd % time encoded-story) (stories/destination-story-sets story))))
                   (bench "user feed queries" (doall (feed/redigest-user-feeds conn user-feeds)))
                   (bench "everything feed queries" (doall (feed/redigest-everything-feed))))))
    (log/info "added" encoded-story)))

(defn- process-story-job!
  [conn json-message]
  (let [msg (json/read-json json-message)
        args (:args msg)]
    (case (:class msg)
      "Stories::AddInterestInListing" (add-interest! conn :listing args)
      "Stories::AddBatchInterestsInListing" (add-interests! conn :listing args)
      "Stories::AddInterestInActor" (add-interest! conn :actor args)
      "Stories::AddInterestInTag" (add-interest! conn :tag args)
      "Stories::RemoveInterestInListing" (remove-interest! conn :listing args)
      "Stories::RemoveInterestInActor" (remove-interest! conn :actor args)
      "Stories::RemoveInterestInTag" (remove-interest! conn :tag args)
      "Stories::Create" (add-story! conn (reduce merge args)))))

(defn process-story-jobs-from-queue!
  [run? connection-specs queue-keys]
  (let [resque-conn (:resque connection-specs)
        feed-conn (:feeds connection-specs)]
   ;; doseq is not lazy, and does not retain the head of the seq: perfect!
   (doseq [json-message (take-while identity (resque/jobs run? resque-conn queue-keys))]
     (try
       (process-story-job! feed-conn json-message)
       (catch Exception e
         (log/error "failed to process job:" json-message "with" e)
         (safe-print-stack-trace e "jobs"))))))

