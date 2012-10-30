(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [config :as config]]
            [risingtide.model [feed :refer [add]]]
            [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [risingtide.feed
             [expiration :refer [expire]]
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed]]]
            [risingtide.feed.persist.shard :as shard]
            [risingtide.model.feed.digest :refer [new-digest-feed]]
            [risingtide.model
             [timestamps :refer [min-timestamp max-timestamp]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log])
  (:import java.util.concurrent.ScheduledThreadPoolExecutor))

(defn update-feed-set! [feed-set-atom user-id story]
  (if-let [feed-atom (@feed-set-atom user-id)]
    (swap! feed-atom #(add % story))
    (swap! feed-set-atom #(assoc % user-id (atom (new-digest-feed story))))))

(defn expire-feed! [feed-atom]
  (swap! feed-atom #(apply new-digest-feed (expire %))))

(defn expire-feeds! [feed-set]
  (map expire-feed! (vals @feed-set)))

(defn schedule-with-delay [function interval]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay function interval interval java.util.concurrent.TimeUnit/SECONDS)))



(defbolt add-to-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})
        feed-expirer (schedule-with-delay
                       #(try
                          (expire-feeds! feed-set)
                             (catch Exception e
                               (log/error "exception expiring cache" e)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" user-id "user-id" story "story" score "score" :as tuple}]
              (update-feed-set! feed-set user-id story)
              (emit-bolt! collector [id (seq @(@feed-set user-id))])
              (ack! collector tuple))
     (cleanup [] (.shutdown feed-expirer)))))

(defbolt add-to-curated-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [feed (atom (new-digest-feed))]
    (bolt
     (execute [tuple]
              (let [{id "id" story "story"} tuple]
                (when (for-everything-feed? story)
                  (swap! feed #(add % story))
                  (emit-bolt! collector [id (seq @feed)]))
                (ack! collector tuple))))))

(defn serialize [{id "id" feed "feed"} collector]
  (emit-bolt! collector [id (with-out-str (print (encode-feed feed)))]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))
