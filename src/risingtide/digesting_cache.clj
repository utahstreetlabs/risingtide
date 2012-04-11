(ns risingtide.digesting-cache
  (:use risingtide.core)
  (:require [clojure.tools.logging :as log]
            [clojure.set :as set]
            [risingtide
             [stories :as stories]
             [key :as key]]))

;;;; Das Cache ;;;;

(defn- empty-cache
  ;; take arbitrary args to work with swap
  [& args]
  {:low-score (now)
   :high-score (now)})

(def story-cache (atom (empty-cache)))

(defn reset-cache! [] (swap! story-cache empty-cache))


;;;; Getting Stories ;;;;

(defn cached-stories
  [cache]
  (dissoc cache :low-score :high-score))

(defn stories-for-interests
  "like zunionstore but without the store"
  [cache interesting-keys]
  (distinct (apply concat (map cache interesting-keys))))

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
  (let [scored-story (assoc (assoc story :score score) :encoded (stories/encode story))]
    (assoc (reduce (fn [c key] (assoc c key (conj (or (c key) #{}) scored-story)))
                   cache (stories/destination-story-sets story))
      :high-score (max score (:high-score cache))
      :low-score (min score (:low-score cache)))))

(defn cache-story
  [story score]
  (swap! story-cache add-story story score))

(defn add!
  [redii story time]
  (cache-story story time)
  (stories/add! redii story time))

(defn preload!
  [redii ttl]
  (doseq [[story score] (stories/range-with-scores redii (- (now) ttl) (now))]
    (cache-story (stories/decode story) (Long. score))))

;;;; Cache Expiration ;;;;

(defn update-low-score
  [cache score]
  (assoc cache :low-score (min score (:high-score cache))))

(defn expire-cached-stories
  [cache-to-expire low-score]
  (try
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

     (swap! cache-to-expire
            #(bench "attempting to expire cached stories"
                    (update-low-score (reduce expire-cached-story-set % (cached-stories %)) low-score))))
   (catch Throwable t (log/error t) (safe-print-stack-trace t))))

(defn cache-expirer
  [cache-to-expire expire-every-ms ttl]
  (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
    (.scheduleWithFixedDelay
     #(expire-cached-stories cache-to-expire (- (now) ttl))
     0 expire-every-ms java.util.concurrent.TimeUnit/SECONDS)))
