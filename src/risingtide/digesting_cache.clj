(ns risingtide.digesting-cache
  (:use risingtide.core)
  (:require [clojure.set :as set]
            [risingtide.stories :as stories]))

(defn- empty-cache
  ;; take arbitrary args to work with swap
  [& args]
  {:low-score (now)
   :high-score (now)})

;; TODO: need a cache expiration thread
(def story-cache (atom (empty-cache)))

(defn cached-stories
  [cache]
  (dissoc cache :low-score :high-score))

(defn reset-cache! [] (swap! story-cache empty-cache))

(defn stories-for-interests
  "like zunionstore but without the store"
  [cache interesting-keys]
  (apply set/union (map cache interesting-keys)))

(defn all-stories
  [cache]
  (apply set/union (vals (cached-stories cache))))

(defn add-story
  [cache story score]
  (let [scored-story (assoc story :score score)]
    (assoc (reduce (fn [c key] (assoc c key (conj (or (c key) #{}) scored-story)))
                   cache
                   (stories/destination-story-sets story))
      :high-score score)))

(defn cache-story
  [story score]
  (swap! story-cache add-story story score))