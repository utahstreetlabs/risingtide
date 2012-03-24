(ns risingtide.digesting-cache
  (:use risingtide.core)
  (:require [clojure.set :as set]
            [risingtide
             [stories :as stories]
             [key :as key]])
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

(defn all-stories-with-key-prefix
  [cache prefix]
  (let [card-prefix (key/format-key prefix)]
   (apply set/union
          (for [[key val] (cached-stories cache)]
            (when (.startsWith key card-prefix) val)))))

(defn all-card-stories
  [cache]
  (all-stories-with-key-prefix cache "c"))

(defn all-network-stories
  [cache]
  (all-stories-with-key-prefix cache "n"))

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
  [run? cache-to-expire expire-every-ms ttl]
  (future
    (loop [last-run (now)]
      (let [run-next (+ last-run expire-every-ms)
            expiration-time (-  (.getTime (Date.)) ttl)]
        (expire-cached-stories cache-to-expire expiration-time)
        (while (and @run? (< (now) run-next)) (Thread/sleep 500))
        (when @run? (recur run-next))))))
