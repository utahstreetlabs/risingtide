(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [core :refer [log-err]]
             [dedupe :refer [dedupe]]
             [key :as key]
             [config :as config]
             [redis :as redis]
             [active-users :refer [active-users active?]]]
            [risingtide.feed
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed write-feed! initialize-digest-feed]]
             [set :as feed-set]]
            [risingtide.model
             [feed :refer [add]]
             [timestamps :refer [timestamp]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [meters :refer [defmeter mark!]]
             [timers :refer [deftimer time!]]
             [gauges :refer [gauge]]])
  (:import java.util.concurrent.ScheduledThreadPoolExecutor))

(defn schedule-with-delay [function interval]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay function interval interval java.util.concurrent.TimeUnit/SECONDS)))

(defmeter expiration-run "expiration runs")
(deftimer expiration-time)
(defmeter feed-writes "feeds written")
(deftimer add-feed-time)
(deftimer feed-write-time)

(defbolt add-to-feed ["id" "user-id" "feed"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)
        feed-set (atom {})
        feed-set-size-gauge (gauge "feed-set-size" (count @feed-set))
        feed-expirer (schedule-with-delay
                       #(try
                          (time! expiration-time (feed-set/expire! redii feed-set))
                          (mark! expiration-run)
                          (catch Exception e (log-err "exception expiring cache" e *ns*)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" message "message" user-id "user-id" story "story" new-feed "feed" listing-id "listing-id" :as tuple}]
              (case message
                :remove (feed-set/remove! redii feed-set user-id listing-id)

                (doseq [s (if story [story] new-feed)]
                  (feed-set/add! redii feed-set user-id (dedupe s))))
              (when (and (or story (= :remove message) (not (empty? new-feed)))
                         (active? redii user-id))
                  (let [feed @(@feed-set user-id)]
                    (mark! feed-writes)
                    (time! feed-write-time
                           (write-feed! redii (key/user-feed user-id) feed))))

              (ack! collector tuple))
     (cleanup [] (.shutdown feed-expirer)))))

(defmeter curated-feed-writes "stories written to curated feed")

(defbolt add-to-curated-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)
        feed-atom (atom (initialize-digest-feed redii (key/everything-feed)))
        feed-expirer (schedule-with-delay
                       #(try
                          (feed-set/expire-feed! feed-atom)
                          (catch Exception e (log-err "exception expiring cache" e *ns*)))
                       config/feed-expiration-delay)
        curated-feed-size-gauge (gauge "curated-feed-size" (count (seq @feed-atom)))]
    (bolt
     (execute [{id "id" story "story" :as tuple}]
              (when (for-everything-feed? story)
                (swap! feed-atom add (dedupe story))
                (write-feed! redii (key/everything-feed) @feed-atom)
                (mark! curated-feed-writes))
              (ack! collector tuple)))))

(defn feed-to-json [feed]
  (with-out-str (print (encode-feed (map #(assoc % :timestamp (timestamp %)) feed)))))

(defn serialize [{id "id" feed "feed" :as tuple} collector]
  (emit-bolt! collector [id (feed-to-json feed)]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))
