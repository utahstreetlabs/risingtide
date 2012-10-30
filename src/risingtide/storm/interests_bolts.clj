(ns risingtide.storm.interests-bolts
  (:require [risingtide.interests
             [brooklyn :as follows]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]))

(defn like-scores [user-ids story]
  (likes/like-counts (:listing-id story) user-ids))

(defbolt like-interest-scorer ["id" "user-id" "story" "score" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (doseq [[user-id score] (like-scores user-ids story)]
    (emit-bolt! collector [id user-id story score :like]))
  (ack! collector tuple))

(defn follow-scores [user-ids story]
  (follows/follow-counts (:actor-id story) user-ids))

(defbolt follow-interest-scorer ["id" "user-id" "story" "score" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (doseq [[user-id score] (follow-scores user-ids story)]
    (emit-bolt! collector [id user-id story score :follow]))
  (ack! collector tuple))

(defn sum-scores [scores]
  (apply + (vals scores)))

(defbolt interest-reducer {"default" ["id" "user-id" "story" "score"]
                           "story"   ["id" "user-id" "story"]} {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [{id "id" user-id "user-id" story "story" type "type" score "score" :as  tuple}]
              (swap! scores #(assoc-in % [[user-id story] type] score))
              (let [story-scores (get @scores [user-id story])
                    scored-types (set (keys story-scores))
                    total-score (sum-scores story-scores)]
                (when  (= scored-types #{:follow :like})
                  (if (>= total-score 1)
                    (do
                      (emit-bolt! collector [id user-id story total-score])
                      (emit-bolt! collector [id user-id story] :stream "story")
                      (swap! scores #(dissoc % [user-id story])))
                    (emit-bolt! collector [id user-id nil] :stream "story"))))
              (ack! collector tuple)))))
