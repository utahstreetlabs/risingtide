(ns risingtide.digesting-cache
  (:use risingtide.core)
  (:require [clojure.set :as set]
            [risingtide.stories :as stories])
  (:import java.util.Date))

;;;; Das Cache ;;;;

(defn- empty-cache
  ;; take arbitrary args to work with swap
  [& args]
  {:low-score 0
   :high-score 0})

(def story-cache (atom (empty-cache)))

(defn reset-cache! [] (swap! story-cache empty-cache))


;;;; Getting Stories ;;;;

(defn cached-stories
  [cache]
  (dissoc cache :low-score :high-score))

(defn stories-for-interests
  "like zunionstore but without the store"
  [cache interesting-keys]
  (apply set/union (map cache interesting-keys)))

(defn all-stories
  [cache]
  (apply set/union (vals (cached-stories cache))))


;;;; Adding Stories ;;;;

(defn add-story
  [cache story score]
  (let [scored-story (assoc story :score score)]
    (assoc (reduce (fn [c key] (assoc c key (conj (or (c key) #{}) scored-story)))
                   cache (stories/destination-story-sets story))
      :high-score (max score (:high-score cache))
      :low-score (min score (:low-score cache)))))

(defn cache-story
  [story score]
  (swap! story-cache add-story story score))


;;;; Cache Expiration ;;;;

(defn expire-cached-stories
  [cache-to-expire low-score]
  (letfn
      [(expire-cached-story
         [set story]
         (if (> (:score story) low-score)
           (conj set story)
           set))
       (expire-cached-story-set
         [cache [key story-set]]
         (let [new-story-set (reduce expire-cached-story #{} story-set)]
           (if (empty? new-story-set)
             (dissoc cache key)
             (assoc cache key new-story-set))))]

    (swap! cache-to-expire #(assoc (reduce expire-cached-story-set % (cached-stories %))
                             :low-score low-score))))

(defn cache-expiration-thread
  [cache-to-expire expire-every-ms ttl]
  (future
    (while true
      (let [expiration-time (-  (.getTime (Date.)) ttl)]
       (expire-cached-stories cache-to-expire expiration-time))
      (Thread/sleep expire-every-ms))))
