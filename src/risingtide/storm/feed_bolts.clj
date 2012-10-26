(ns risingtide.storm.feed-bolts
  (:require [risingtide.model
             [feed :refer [add]]]
            [risingtide.model.feed
             [digest :refer [new-digest-feed]]]
            [risingtide.feed
             [filters :refer [for-everything-feed?]]]
            [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [risingtide.feed.persist :refer [encode-feed]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defn update-feed-set! [feed-set user-id story]
  (swap! feed-set #(update-in % [user-id] (fn [v] (add (or v (new-digest-feed)) story)))))

(defbolt add-to-feed ["feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})]
    (bolt
     (execute [{user-id "user-id" story "story" score "score" :as tuple}]
              (update-feed-set! feed-set user-id story)
              (emit-bolt! collector [(seq (@feed-set user-id))])
              (ack! collector tuple)))))

(defbolt add-to-curated-feed ["feed"] {:prepare true}
  [conf context collector]
  (let [feed (atom (new-digest-feed))]
    (bolt
     (execute [tuple]
              (let [{story "story"} tuple]
                (when (for-everything-feed? story)
                  (swap! feed #(add % story))
                  (emit-bolt! collector [(seq @feed)]))
                (ack! collector tuple))))))

(defn serialize [{id "id" feed "feed"} collector]
  (emit-bolt! collector [id (with-out-str (print (encode-feed feed)))]))

(defbolt serialize-feed ["id" "feed"] [tuple collector]
  (serialize tuple collector)
  (ack! collector tuple))
