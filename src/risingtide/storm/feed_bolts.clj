(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [core :refer [now]]
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
            [clojure.tools.logging :as log])
  (:import java.util.concurrent.ScheduledThreadPoolExecutor))

(defn initialize-digest-feed [redii feed-key & stories]
  (let [initial-stories (feed redii feed-key (expiration-threshold) (now))]
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
   (swap! feed-set #(select-keys actives %))
   (delete-feeds! redii (clojure.set/difference (set (keys @feed-set)) (set actives))))
  (doall (map expire-feed! (vals @feed-set))))

(defn schedule-with-delay [function interval]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay function interval interval java.util.concurrent.TimeUnit/SECONDS)))

(defbolt add-to-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})
        redii (redis/redii)
        feed-expirer (schedule-with-delay
                       #(try
                          (expire-feeds! redii feed-set)
                             (catch Exception e
                               (log/error "exception expiring cache" e)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [{id "id" user-id "user-id" story "story" score "score" :as tuple}]
              (update-feed-set! redii feed-set user-id story)
              (let [feed @(@feed-set user-id)]
                (when (active? redii user-id)
                 (write-feed! redii (key/user-feed user-id) feed))
                (emit-bolt! collector [id (seq feed)]))
              (ack! collector tuple))
     (cleanup [] (.shutdown feed-expirer)))))

(defbolt add-to-curated-feed ["id" "feed"] {:prepare true}
  [conf context collector]
  (let [redii (redis/redii)
        feed-atom (initialize-digest-feed redii (key/everything-feed))
        feed-expirer (schedule-with-delay
                       #(try
                          (expire-feed! feed-atom)
                          (catch Exception e
                            (log/error "exception expiring cache" e)))
                       config/feed-expiration-delay)]
    (bolt
     (execute [tuple]
              (let [{id "id" story "story"} tuple]
                (when (for-everything-feed? story)
                  (swap! feed-atom add story)
                  (write-feed! redii (key/everything-feed) @feed-atom)
                  (emit-bolt! collector [id (seq @feed-atom)]))
                (ack! collector tuple))))))

(defn serialize [{id "id" feed "feed"} collector]
  (emit-bolt! collector [id (with-out-str (print (encode-feed feed)))]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))
