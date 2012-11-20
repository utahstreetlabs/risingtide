(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [core :refer [now log-err]]
             [config :as config]
             [redis :as redis]
             [key :as key]
             [active-users :refer [active-users active?]]]
            [risingtide.model [feed :refer [add]]]
            [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [risingtide.feed
             [expiration :refer [expire expiration-threshold]]
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed write-feed! feed delete-feeds!]]]
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
  (let [initial-stories (time! feed-load-time (feed redii feed-key (expiration-threshold) (now)))]
    (atom (apply new-digest-feed (concat initial-stories stories)))))

(defn update-feed-set! [redii feed-set-atom user-id story]
  (if-let [feed-atom (@feed-set-atom user-id)]
    (swap! feed-atom add story)
    (swap! feed-set-atom
           #(assoc % user-id (initialize-digest-feed redii (key/user-feed user-id) story)))))

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
    (map count (map deref (vals feed-set)))))

(defn mean
  [& numbers]
  (quot (apply + numbers) (count numbers)))

(defn median [& ns]
  "Thanks, http://rosettacode.org/wiki/Averages/Median#Clojure"
  (let [ns (sort ns)
        cnt (count ns)
        mid (bit-shift-right cnt 1)]
    (if (odd? cnt)
      (nth ns mid)
      (/ (+ (nth ns mid) (nth ns (dec mid))) 2))))

(defmeter expiration-run "expiration runs")
(deftimer expiration-time)
(defmeter feed-writes "feeds written")
(deftimer feed-write-time)

(defbolt add-to-feed ["id" "user-id" "feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})
        feed-set-size-gauge (gauge "feed-set-size" (count @feed-set))
        feed-max-gauge (gauge "feed-set-feed-min-size" (apply max (feed-set-feed-sizes @feed-set)))
        feed-min-gauge (gauge "feed-set-feed-max-size" (apply min (feed-set-feed-sizes @feed-set)))
        feed-mean-gauge (gauge "feed-set-feed-mean-size" (apply mean (feed-set-feed-sizes @feed-set)))
        feed-median-gauge (gauge "feed-set-feed-median-size" (apply median (feed-set-feed-sizes @feed-set)))
        redii (redis/redii)
        feed-expirer (schedule-with-delay
                       #(try
                          (time! expiration-time (expire-feeds! redii feed-set))
                          (mark! expiration-run)
                          (catch Exception e (log-err "exception expiring cache" e *ns*)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" user-id "user-id" story "story" new-feed "feed" :as tuple}]
              (doseq [s (if story [story] new-feed)]
                (update-feed-set! redii feed-set user-id s))
              (when (or story (not (empty? new-feed)))
               (let [feed @(@feed-set user-id)]
                 (when (active? redii user-id)
                   (mark! feed-writes)
                   (time! feed-write-time (write-feed! redii (key/user-feed user-id) feed)))
                 (emit-bolt! collector [id user-id (seq feed)] :anchor tuple)))
              (ack! collector tuple))
     (cleanup [] (.shutdown feed-expirer)))))

(defbolt add-to-curated-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)
        feed-atom (initialize-digest-feed redii (key/everything-feed))
        feed-expirer (schedule-with-delay
                       #(try
                          (expire-feed! feed-atom)
                          (catch Exception e (log-err "exception expiring cache" e *ns*)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" story "story" :as tuple}]
              (when (for-everything-feed? story)
                (swap! feed-atom add story)
                (write-feed! redii (key/everything-feed) @feed-atom)
                (emit-bolt! collector [id (seq @feed-atom)] :anchor tuple))
              (ack! collector tuple)))))

(defn serialize [{id "id" feed "feed" :as tuple} collector]
  (emit-bolt! collector [id (with-out-str (print (encode-feed feed :include-ts true)))] :anchor tuple))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))
