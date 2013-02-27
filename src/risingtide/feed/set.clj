(ns risingtide.feed.set
  (:require [risingtide
             [dedupe :refer [dedupe]]
             [key :as key]
             [active-users :refer [active-users]]]
            [risingtide.feed
             [expiration :refer [expire]]
             [filters :refer [for-everything-feed?]]
             [persist :refer [encode-feed write-feed! load-feed delete-feeds! initialize-digest-feed]]]
            [risingtide.model
             [feed :refer [add remove-listing]]
             [timestamps :refer [timestamp]]]
            [risingtide.model.feed.digest :refer [new-digest-feed]]))

(defn add! [redii feed-set-atom user-id story]
  (if-let [feed-atom (@feed-set-atom user-id)]
    (swap! feed-atom add story)
    (swap! feed-set-atom
           #(assoc % user-id (atom
                              (initialize-digest-feed redii (key/user-feed user-id) story))))))

(defn remove! [redii feed-set-atom user-id listing-id]
  (if-let [feed-atom (@feed-set-atom user-id)]
      (swap! feed-atom remove-listing listing-id)
      (swap! feed-set-atom
             #(assoc % user-id (atom (remove-listing (initialize-digest-feed redii (key/user-feed user-id)) listing-id))))))

(defn expire-feed! [feed-atom]
  (swap! feed-atom #(apply new-digest-feed (expire %))))

(defn expire-inactive! [redii feed-set]
  (let [actives (active-users redii)
        active-feed-users (keys @feed-set)]
    (swap! feed-set #(select-keys % actives))
    (delete-feeds! redii (clojure.set/difference (set active-feed-users) (set actives)))))

(defn expire! [redii feed-set]
  (expire-inactive! redii feed-set)
  (doall (map expire-feed! (vals @feed-set))))