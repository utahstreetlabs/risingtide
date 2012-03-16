(ns risingtide.digesting-cache
  (:require [clojure.set :as set]
            [risingtide.stories :as stories]))

(def story-cache (atom {}))

(defn reset-cache! [] (swap! story-cache (constantly {})))

(defn stories-for-interests
  "like zunionstore but without the store"
  [interest-keys]
  (apply set/union (map story-cache interest-keys)))

(defn add-story
  [cache story]
  (reduce (fn [c key] (assoc c key (conj (or (c key) #{}) story))) cache
          (stories/destination-story-sets story)))

(defn cache-story
  [story]
  (swap! story-cache add-story story))
