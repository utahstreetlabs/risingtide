(ns risingtide.storm.interests-bolts
  (:require [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defn like-score [user-id story]
  (if (likes/likes? user-id (:listing-id story)) 1 0))

(defbolt like-interest-scorer ["user-id" "story" "score" "type"]  [tuple collector]
  (let [{user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [user-id story (like-score user-id story) :like]))
  (ack! collector tuple))


(defn follow-score [user-id story]
  (if (follows/following? user-id (:actor-id story)) 1 0))

(defbolt follow-interest-scorer ["user-id" "story" "score" "type"]  [tuple collector]
  (let [{user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [user-id story (follow-score user-id story) :follow]))
  (ack! collector tuple))


(defbolt interest-reducer ["user-id" "story" "score"] {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [tuple]
              (let [{user-id "user-id" story "story" type "type" score "score"} tuple]
                (swap! scores #(assoc-in % [[user-id story] type] score))
                (let [story-scores (get @scores [user-id story])
                      scored-types (set (keys story-scores))]
                  (when (= scored-types #{:follow :like})
                    (let [total-score (apply + (vals story-scores))]
                      (when (>= total-score 1) (emit-bolt! collector [user-id story total-score])))
                    (swap! scores #(dissoc % [user-id story])))))
              (ack! collector tuple)))))
