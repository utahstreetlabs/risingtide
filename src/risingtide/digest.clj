(ns risingtide.digest
  (:use risingtide.core)
  (:require [risingtide.stories :as story]))

(defn digest-story
  "stick a story into a digest aggregator

when used to reduce a list of stories, produces a nested map with listing ids
on the outside, types underneath that, and actor ids inside that
"
  [digested story]
  (assoc-in digested [:listings (story :listing_id) (story :type) (story :actor_id)] story))

(defn actor-scores
  [actors]
  (for [[id story] actors] [(:score story) id]))

(defn ordered-actors
  "given a map from actor id to stories, return actor ids
in order of story score"
  [actors]
  (map second (sort (actor-scores actors))))

(defn max-actor-score
  [actors]
  (apply max (map first  (actor-scores actors))))

(defn ordered-actions
  "given a map from action name to actors, return action names
in order of the score of the first story for that actor"
  [actions]
  (map second (sort (for [[id actors] actions] [(:score (second (first actors))) id]))))

(defn max-action-score
  [actions]
  (apply max (map :score (apply concat (map vals (vals actions))))))

(defn single-listing-digest-story
  [[listing-id actions]]
  (if (= 1 (count actions))
    (let [[action actors] (first actions)]
      (if (= 1 (count actors))
        (let [[_ story] (first actors)] story)
        (story/multi-actor-digest listing-id action (ordered-actors actors) (max-actor-score actors))))
    (let [actors (distinct (flatten (map keys (vals actions))))]
      (if (= 1 (count actors))
        (story/multi-action-digest listing-id (first actors) (ordered-actions actions) (max-action-score actions))
        (story/multi-actor-multi-action-digest
         listing-id
         (reduce (fn [m [action actors]] (assoc m action (ordered-actors actors))) {} actions)
         (max-action-score actions))))))

(defn digest
  [feed]
  (let [digested (reduce digest-story {} feed)]
    (let [single-listing-stories (map single-listing-digest-story (:listings digested))
          ;;TODO single-actor-stories (map single-actor-digest-story (:actors digested))
          ]
      single-listing-stories)))
