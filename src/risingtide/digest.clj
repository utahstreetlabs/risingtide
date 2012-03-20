(ns risingtide.digest
  (:use risingtide.core)
  (:require [risingtide.stories :as story]))

(defn digest-story
  [digested story]
  (assoc-in digested [:listings (story :listing_id) (story :type) (story :actor_id)] story))

(defn single-listing-digest-story
  [[listing-id actions]]
  (if (= 1 (count actions))
    (let [[action actors] (first actions)]
      (if (= 1 (count actors))
        (second (first actors))
        (story/multi-actor-digest listing-id action (keys actors))))
    (let [actors (distinct (flatten (map keys (vals actions))))]
      (if (= 1 (count actors))
        (story/multi-action-digest listing-id (first actors) (keys actions))
        (story/multi-actor-multi-action-digest
         listing-id
         (reduce (fn [m [action actors]] (assoc m action (keys actors))) {} actions))))))

(defn digest
  [feed]
  (let [digested (reduce digest-story {} feed)]
    (let [single-listing-stories (map single-listing-digest-story (:listings digested))
          ;;TODO single-actor-stories (map single-actor-digest-story (:actors digested))
          ]
      single-listing-stories)))
