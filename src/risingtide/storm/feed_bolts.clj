(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [core :refer [now log-err]]
             [dedupe :refer [dedupe]]
             [config :as config]
             [redis :as redis]
             [key :as key]
             [active-users :refer [active-users active?]]
             [metrics :refer [mean median]]]
            [risingtide.model [feed :refer [add remove-listing]]
             [timestamps :refer [timestamp]]]
            [risingtide.feed
             [expiration :refer [expire expiration-threshold]]
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed write-feed! load-feed delete-feeds!]]]
            [risingtide.feed.persist.shard :as shard]
            [risingtide.model.feed.digest :refer [new-digest-feed]]
            [risingtide.model
             [timestamps :refer [min-timestamp max-timestamp]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [meters :refer [defmeter mark!]]
             [timers :refer [deftimer time!]]
             [gauges :refer [gauge]]])
  (:import java.util.concurrent.ScheduledThreadPoolExecutor))

(deftimer feed-load-time)

(defn initialize-digest-feed [redii feed-key & stories]
  (let [initial-stories (time! feed-load-time (load-feed redii feed-key (expiration-threshold) (now)))]
    (apply new-digest-feed (map dedupe (concat initial-stories stories)))))

(defn add-to-feed-set! [redii feed-set-atom user-id story]
  (if-let [feed-atom (@feed-set-atom user-id)]
    (swap! feed-atom add story)
    (swap! feed-set-atom
           #(assoc % user-id (atom (initialize-digest-feed redii (key/user-feed user-id) story))))))

(defn remove-from-feed-set! [redii feed-set-atom user-id listing-id]
  (if-let [feed-atom (@feed-set-atom user-id)]
      (swap! feed-atom remove-listing listing-id)
      (swap! feed-set-atom
             #(assoc % user-id (atom (remove-listing (initialize-digest-feed redii (key/user-feed user-id)) listing-id))))))

(defn expire-feed! [feed-atom]
  (swap! feed-atom #(apply new-digest-feed (expire %))))

(defn expire-feeds! [redii feed-set]
  (let [actives (active-users redii)]
    (swap! feed-set #(select-keys % actives))
    (delete-feeds! redii (clojure.set/difference (set (keys @feed-set)) (set actives))))
  (doall (map expire-feed! (vals @feed-set))))

(defn schedule-with-delay [function interval]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay function interval interval java.util.concurrent.TimeUnit/SECONDS)))

(defn feed-set-feed-sizes [feed-set]
  (if (empty? feed-set)
    [-1]
    (map count (map seq (map deref (vals feed-set))))))

(defmeter expiration-run "expiration runs")
(deftimer expiration-time)
(defmeter feed-writes "feeds written")
(deftimer add-feed-time)
(deftimer feed-write-time)

(defbolt add-to-feed ["id" "user-id" "feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})
        feed-set-size-gauge (gauge "feed-set-size" (count @feed-set))
;;        feed-max-gauge (gauge "feed-set-feed-min-size" (apply min (feed-set-feed-sizes @feed-set)))
;;        feed-min-gauge (gauge "feed-set-feed-max-size" (apply max (feed-set-feed-sizes @feed-set)))
;;        feed-mean-gauge (gauge "feed-set-feed-mean-size" (apply mean (feed-set-feed-sizes @feed-set)))
;;        feed-median-gauge (gauge "feed-set-feed-median-size" (int (apply median (feed-set-feed-sizes @feed-set))))
        redii (redis/redii)
        feed-expirer (schedule-with-delay
                       #(try
                          (time! expiration-time (expire-feeds! redii feed-set))
                          (mark! expiration-run)
                          (catch Exception e (log-err "exception expiring cache" e *ns*)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" message "message" user-id "user-id" story "story" new-feed "feed" listing-id "listing-id" :as tuple}]
              (case message
                :remove (remove-from-feed-set! redii feed-set user-id listing-id)

                (doseq [s (if story [story] new-feed)]
                  (add-to-feed-set! redii feed-set user-id (dedupe s))))
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
                          (expire-feed! feed-atom)
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
