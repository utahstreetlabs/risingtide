(ns risingtide.storm.interests-bolts
  (:require [risingtide.core :refer [bench]]
            [risingtide.interests
             [brooklyn :as brooklyn]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [timers :refer [deftimer time!]]
             [meters :refer [defmeter mark!]]
             [gauges :refer [gauge]]]))

(defn like-scores [user-ids story]
  (likes/like-counts (:listing-id story) user-ids))

(deftimer like-interest-score-time)

(defbolt like-interest-scorer ["id" "user-id" "story" "score" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! like-interest-score-time (like-scores user-ids story))]
   (when (not (= (count scores) (count user-ids)))
     (log/error "got "count scores" like scores for "(count user-ids)" users"))
   (doseq [[user-id score] scores]
     (emit-bolt! collector [id user-id story score :like] :anchor tuple)))
  (ack! collector tuple))

(defn follow-scores [user-ids story]
  (brooklyn/follow-counts (:actor-id story) user-ids))

(deftimer follow-interest-score-time)

(defbolt follow-interest-scorer ["id" "user-id" "story" "score" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! follow-interest-score-time (follow-scores user-ids story))]
   (when (not (= (count scores) (count user-ids)))
     (log/error "got "count scores" follow scores for "(count user-ids)" users"))
   (doseq [[user-id score] scores]
     (emit-bolt! collector [id user-id story score :follow] :anchor tuple)))
  (ack! collector tuple))

(defn seller-follow-scores [user-ids story]
  (brooklyn/follow-counts (:seller_id (brooklyn/find-listing (:listing-id story))) user-ids))

(deftimer seller-follow-interest-score-time)

(defbolt seller-follow-interest-scorer ["id" "user-id" "story" "score" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! seller-follow-interest-score-time (seller-follow-scores user-ids story))]
    (when (not (= (count scores) (count user-ids)))
      (log/error "got "count scores" seller follow scores for "(count user-ids)" users"))
    (doseq [[user-id score] scores]
      (emit-bolt! collector [id user-id story score :listing-seller] :anchor tuple)))
  (ack! collector tuple))

(defn sum-scores [scores]
  (apply + (vals scores)))

(defmeter story-scored "stories scored")

(defbolt interest-reducer {"default" ["id" "user-id" "story" "score"]
                           "story"   ["id" "user-id" "story"]} {:prepare true}
  [conf context collector]
  (let [scores (atom {})]
    (bolt
     (execute [{id "id" user-id "user-id" story "story" type "type" score "score" :as tuple}]
              (swap! scores #(assoc-in % [[user-id story] type] score))
              (let [story-scores (get @scores [user-id story])
                    total-score (sum-scores story-scores)
                    interest-reducer-size-gauge (gauge "interest-reducer-size" (count @scores))]
                (when (= (set (keys story-scores)) #{:follow :like :listing-seller})
                  (swap! scores #(dissoc % [user-id story]))
                  (if (>= total-score 1)
                    (do
                      (emit-bolt! collector [id user-id story total-score] :anchor tuple)
                      (emit-bolt! collector [id user-id story] :stream "story" :anchor tuple)
                      (mark! story-scored))
                    (emit-bolt! collector [id user-id nil] :stream "story" :anchor tuple))))
              (ack! collector tuple)))))
