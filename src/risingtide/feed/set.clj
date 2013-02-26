(ns risingtide.feed.set
  (:require [risingtide
             [core :refer [now]]
             [dedupe :refer [dedupe]]
             [key :as key]
             [active-users :refer [active-users active?]]]
            [risingtide.feed
             [expiration :refer [expire expiration-threshold]]
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed write-feed! load-feed delete-feeds!]]]
            [risingtide.model
             [feed :refer [add remove-listing]]
             [timestamps :refer [timestamp]]]
            [risingtide.model.feed.digest :refer [new-digest-feed]]
            [metrics
             [timers :refer [deftimer time!]]]))

(deftimer feed-load-time)

(defn initialize-digest-feed [redii feed-key & stories]
  (let [initial-stories (time! feed-load-time (load-feed redii feed-key (expiration-threshold) (now)))]
    (apply new-digest-feed (map dedupe (concat initial-stories stories)))))

(defn add! [redii feed-set-atom user-id story]
  (if-let [feed-atom (@feed-set-atom user-id)]
    (swap! feed-atom add story)
    (swap! feed-set-atom
           #(assoc % user-id (atom (initialize-digest-feed redii (key/user-feed user-id) story))))))

(defn remove! [redii feed-set-atom user-id listing-id]
  (if-let [feed-atom (@feed-set-atom user-id)]
      (swap! feed-atom remove-listing listing-id)
      (swap! feed-set-atom
             #(assoc % user-id (atom (remove-listing (initialize-digest-feed redii (key/user-feed user-id)) listing-id))))))

(defn expire-feed! [feed-atom]
  (swap! feed-atom #(apply new-digest-feed (expire %))))

(defn expire! [redii feed-set]
  (let [actives (active-users redii)]
    (swap! feed-set #(select-keys % actives))
    (delete-feeds! redii (clojure.set/difference (set (keys @feed-set)) (set actives))))
  (doall (map expire-feed! (vals @feed-set))))