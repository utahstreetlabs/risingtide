(ns risingtide.storm.interests-bolts
  (:require [risingtide
             [core :refer [bench]]
             [dedupe :refer [dedupe]]]

            [risingtide.interests
             [brooklyn :as brooklyn]
             [pyramid :as likes]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [timers :refer [deftimer time!]]
             [meters :refer [defmeter mark!]]
             [gauges :refer [gauge]]]))

(defn- counts-to-scores [counts user-ids]
  (dissoc
   (->> (or counts {})
        (map (fn [{cnt :cnt user-id :user_id}] [user-id cnt]))
        (into {})
        (merge (reduce #(assoc %1 %2 0) {} user-ids)))
   nil))

(defn like-scores [user-ids story]
  (counts-to-scores (likes/like-counts (:listing-id story) user-ids) user-ids))

(deftimer like-interest-score-time)

(defbolt like-interest-scorer ["id" "user-ids-hash" "story" "scores" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! like-interest-score-time (like-scores user-ids story))]
   (when (not (= (count scores) (count user-ids)))
     (log/error "got "count scores" like scores for "(count user-ids)" users"))
   (emit-bolt! collector [id (.hashCode user-ids) story scores :like] :anchor tuple))
  (ack! collector tuple))

(defn tag-like-scores [user-ids story]
  (counts-to-scores (likes/tag-like-counts (:tag-ids story) user-ids) user-ids))

(deftimer tag-like-interest-score-time)

(defbolt tag-like-interest-scorer ["id" "user-ids-hash" "story" "scores" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! tag-like-interest-score-time (tag-like-scores user-ids story))]
   (when (not (= (count scores) (count user-ids)))
     (log/error "got "count scores" tag like scores for "(count user-ids)" users"))
   (emit-bolt! collector [id (.hashCode user-ids) story scores :tag-like] :anchor tuple))
  (ack! collector tuple))

(defn follow-scores [user-ids story]
  (counts-to-scores (brooklyn/follow-counts (:actor-id story) user-ids) user-ids))

(deftimer follow-interest-score-time)

(defbolt follow-interest-scorer ["id" "user-ids-hash" "story" "scores" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! follow-interest-score-time (follow-scores user-ids story))]
   (when (not (= (count scores) (count user-ids)))
     (log/error "got "count scores" follow scores for "(count user-ids)" users"))
   (emit-bolt! collector [id (.hashCode user-ids) story scores :follow] :anchor tuple))
  (ack! collector tuple))

(defn seller-follow-scores [user-ids story]
  (counts-to-scores (brooklyn/follow-counts (:seller_id (brooklyn/find-listing (:listing-id story))) user-ids) user-ids))

(deftimer seller-follow-interest-score-time)

(defbolt seller-follow-interest-scorer ["id" "user-ids-hash" "story" "scores" "type"]
  [{id "id" user-ids "user-ids" story "story" :as tuple} collector]
  (let [scores (time! seller-follow-interest-score-time (seller-follow-scores user-ids story))]
    (when (not (= (count scores) (count user-ids)))
      (log/error "got "count scores" seller follow scores for "(count user-ids)" users"))
    (emit-bolt! collector [id (.hashCode user-ids) story scores :listing-seller] :anchor tuple))
  (ack! collector tuple))

(defn sum-scores [scores]
  (apply + (vals scores)))

(defmeter story-scored "stories scored")

(defbolt interest-reducer {"default" ["id" "user-id" "story" "score"]} {:prepare true}
  [conf context collector]
  (let [scores-atom (atom {})]
    (bolt
     (execute [{id "id" user-ids-hash "user-ids-hash" story "story" type "type" scores "scores" :as tuple}]


              (swap! scores-atom #(reduce (fn [h [user-id score]] (assoc-in h [[user-id story] type] score)) % scores))
              (doseq [user-id (keys scores)]
                (let [story-scores (get @scores-atom [user-id story])
                      total-score (sum-scores story-scores)
                      interest-reducer-size-gauge (gauge "interest-reducer-size" (count @scores-atom))]
                  (when (= (set (keys story-scores)) #{:follow :like :tag-like :listing-seller})
                    (swap! scores-atom #(dissoc % [user-id story]))
                    (mark! story-scored)
                    (when (>= total-score 1)
                      (emit-bolt! collector [id user-id story total-score] :anchor tuple)))))
              (ack! collector tuple)))))
