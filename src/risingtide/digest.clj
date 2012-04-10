(ns risingtide.digest
  "Story Digesting

Given a set of stories, digesting is done in two passes:

1) First, we iterate over the list of stories and create N indexes of nested maps such that the
   first level of maps represent individual digest story candidates
2) Next, we reduce through the digest story candidates in each index. At each step we either add the
   component stories to a set of stories that will not be digested by this index or we create a new
   digest story and add it to the set of digested stories for this index.
3) Finally, we take the union of the N digested story sets and the intersection of the N undigested story
   sets and build a new feed from the result.

Note that each digesting index creates separate sets of digest stories - individual stories may be
represented in at most one digest story from each index, but may end up in more than one digest story
once digested story sets from each digesting index are unioned.
"
  (:use risingtide.core)
  (:require [risingtide.stories :as story]
            [risingtide.config :as config]
            [clojure.set :as set]))

(def ^:dynamic *single-actor-digest-story-min* config/single-actor-digest-story-min)

(defn digest-story
  "stick a story into a digest aggregator

when used to reduce a list of stories, produces a nested map with listing ids
on the outside, types underneath that, and actor ids inside that
"
  [digested story]
  (if (:listing_id story)
    (-> digested
        (assoc-in [:listings (story :listing_id) (story :type) (story :actor_id)] story)
        (assoc-in [:actors  [(story :actor_id) (story :type)] (story :listing_id)] story))
    (assoc digested :nodigest (cons story (:nodigest digested)))))

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
  (apply max (map first (actor-scores actors))))

(defn ordered-actions
  "given a map from action name to actors, return action names
in order of the score of the first story for that actor"
  [actions]
  (map second (sort (for [[id actors] actions] [(:score (second (first actors))) id]))))

(defn max-action-score
  [actions]
  (apply max (map :score (apply concat (map vals (vals actions))))))

(defn max-listings-score
  [listings]
  (apply max (map :score (vals listings))))

(defn classify-single-listing-digest-story
  [[listing-id actions]]
  (if (= 1 (count actions))
    (let [[action actors] (first actions)]
      (if (= 1 (count actors))
        [:single [(let [[_ story] (first actors)] story)]]
        [:digest [(story/multi-actor-digest listing-id action (ordered-actors actors) (max-actor-score actors))]]))
    (let [actors (distinct (flatten (map keys (vals actions))))]
      (if (= 1 (count actors))
        [:digest
         [(story/multi-action-digest listing-id (first actors) (ordered-actions actions) (max-action-score actions))]]
        [:digest
         [(story/multi-actor-multi-action-digest
           listing-id
           (reduce (fn [m [action actors]] (assoc m action (ordered-actors actors))) {} actions)
           (max-action-score actions))]]))))

(defn classify-single-actor-digest-story
  [[[actor-id action] listings]]
  (if (> (count listings) *single-actor-digest-story-min*) ;; XXX ohgodconstantfixme
    [:digest [(story/multi-listing-digest actor-id action (keys listings) (max-listings-score listings))]]
    [:single (vals listings)]))

(defn bucket-stories
  [classified-stories]
  (reduce (fn [m [type stories]] (assoc m type (into (or (m type) #{}) stories))) {} classified-stories))

(defn digest
  [feed]
  (if (get config/digest env true)
    (let [digested (reduce digest-story {} feed)]
      (let [bucketed-stories
            [(bucket-stories (map classify-single-listing-digest-story (:listings digested)))
             (bucket-stories (map classify-single-actor-digest-story (:actors digested)))]]
        (concat (:nodigest digested)
                (apply set/union (map :digest bucketed-stories))
                (apply set/intersection (map :single bucketed-stories)))))
    feed))
