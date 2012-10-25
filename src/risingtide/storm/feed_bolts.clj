(ns risingtide.storm.feed-bolts
  (:require [risingtide
             [feed :refer [add]]]
            [risingtide.feed
             [digest :refer [new-digest-feed]]
             [filters :refer [for-everything-feed?]]]
            [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defbolt add-to-feed ["feed"] {:prepare true}
  [conf context collector]
  (let [feed-set (atom {})]
    (bolt
     (execute [tuple]
              (let [{user-id "user-id" story "story" score "score"} tuple]
                (swap! feed-set #(update-in % [user-id] (fn [v] (add (or v (new-digest-feed)) story))))
                (emit-bolt! collector [(seq (@feed-set user-id))])
                (ack! collector tuple))))))

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
