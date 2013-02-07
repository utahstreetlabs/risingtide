(ns risingtide.storm.interests-bolts
  (:require [risingtide
             [config :refer [scorer-coefficient]]]
            [risingtide.story.scores :as scores]
            [copious.domain
             [user :as user]
             [collection :as collection]
             [listing :as listing]
             [tag :as tag]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [clojure.tools.logging :as log]
            [metrics
             [timers :refer [deftimer time!]]
             [meters :refer [defmeter mark!]]
             [gauges :refer [gauge]]]))

(defn emit-scores!
  [collector {id "id" user-ids "user-ids" story "story" :as tuple} scores name]
  {:pre [(= (count scores) (count user-ids))]}
  (emit-bolt! collector [id (.hashCode user-ids) story scores name] :anchor tuple))

(defmacro defcountscorer
  "Create a bolt that can be used as a story scorer.

Scorers have a very uniform structure, which lends itself well to abtraction using this
macro. To define a new scorer, first write a 'counter' function that takes a thing to be counted
and a list of user ids and returns a count of the relationships each user has with that thing, like:

    (defn follow-counts [followee-id follower-ids]
      ;; returns [{:user_id 1 :cnt 1} {:user_id 4 :cnt 1}, ...., {:user_id N :cnt 1}]
      )

The counter function may omit users with no relationships in its return value, which should be
an array of hashes. The hashes MUST contain either a user-id or user_id key AND either a cnt or
count key.
"
  [scorer-name counter get-countee]
  (let [timer-name (symbol (str (name scorer-name)"-interest-score-time"))
        scorer-fn-name (symbol (str (name scorer-name)"-scores"))
        bolt-name (symbol (str (name scorer-name)"-interest-scorer"))]
    `(do
       (deftimer ~timer-name)
       (defn ~scorer-fn-name [user-ids# story#]
         (scores/from-counts (~counter (~get-countee story#) user-ids#) user-ids#
                             (scorer-coefficient ~(keyword scorer-name))))
       (defbolt ~bolt-name ["id" "user-ids-hash" "story" "scores" "type"]
         [{user-ids# "user-ids" story# "story" :as tuple#} collector#]
         (time! ~timer-name
                (emit-scores! collector# tuple#
                              (~scorer-fn-name user-ids# story#)
                              ~(keyword scorer-name)))
         (ack! collector# tuple#)))))

(defcountscorer like listing/like-counts :listing-id)

(defcountscorer dislike listing/dislike-counts :listing-id)

(defcountscorer tag-like tag/like-counts :tag-ids)

(defcountscorer follow user/follow-counts :actor-id)

(defcountscorer block user/block-counts :actor-id)

(defcountscorer seller-follow user/follow-counts :seller-id)

(defcountscorer seller-block user/block-counts :seller-id)

(defcountscorer collection-follow collection/follow-counts :listing-id)



(defmeter story-scored "stories scored")

(defbolt interest-reducer {"default" ["id" "user-id" "story" "score"]} {:prepare true}
  [conf context collector]
  (let [scores-atom (atom {})]
    (bolt
     (execute [{id "id" user-ids-hash "user-ids-hash" story "story" type "type" scores "scores" :as tuple}]
              (swap! scores-atom #(reduce (fn [h [user-id score]] (assoc-in h [[user-id story] type] score)) % scores))
              (doseq [user-id (keys scores)]
                (let [story-scores (get @scores-atom [user-id story])
                      total-score (scores/sum story-scores)
                      interest-reducer-size-gauge (gauge "interest-reducer-size" (count @scores-atom))]
                  (when (= (set (keys story-scores)) #{:follow :like :tag-like :block
                                                       :seller-follow :collection-follow :dislike
                                                       :seller-block})
                    (swap! scores-atom #(dissoc % [user-id story]))
                    (mark! story-scored)
                    (when (>= total-score 1)
                      (emit-bolt! collector [id user-id story total-score] :anchor tuple)))))
              (ack! collector tuple)))))
