(ns risingtide.storm.interests-bolts
  (:require [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defn like-score [user-id story]
  (if (likes/likes? user-id (:listing-id story)) 1 0))

(defbolt like-interest-scorer ["id" "user-id" "story" "score" "type"]  [tuple collector]
  (let [{id "id" user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [id user-id story (like-score user-id story) :like]))
  (ack! collector tuple))


(defn follow-score [user-id story]
  (if (follows/following? user-id (:actor-id story)) 1 0))

(defbolt follow-interest-scorer ["id" "user-id" "story" "score" "type"]  [tuple collector]
  (let [{id "id" user-id "user-id" story "story"} tuple]
    (emit-bolt! collector [id user-id story (follow-score user-id story) :follow]))
  (ack! collector tuple))


(defn sum-scores [scores]
  (apply + (vals scores)))

(defbolt interest-reducer {"default" ["id" "user-id" "story" "score"]
                           "story"   ["id" "user-id" "story"]} {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [tuple]
              (let [{id "id" user-id "user-id" story "story" type "type" score "score"} tuple]
                (swap! scores #(assoc-in % [[user-id story] type] score))
                (let [story-scores (get @scores [user-id story])
                      scored-types (set (keys story-scores))
                      total-score (sum-scores story-scores)]
                  (when  (= scored-types #{:follow :like})
                    (if (>= total-score 1)
                      (do
                        (prn "YEAYEA")
                       (emit-bolt! collector [id user-id story total-score])
                       (emit-bolt! collector [id user-id story] :stream "story")
                       (swap! scores #(dissoc % [user-id story])))
                      (emit-bolt! collector [id user-id nil] :stream "story")))))
              (ack! collector tuple)))))
