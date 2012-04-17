(ns risingtide.dgest
  (:use risingtide.core)
  (:require [risingtide.stories :as story]
            [risingtide.feed :as feed]
            [risingtide.config :as config]
            [clojure.tools.logging :as log]
            [accession.core :as redis]
            [clojure.set :as set]))



(defn feed-load-cmd
  ([feed-key since until]
     {:pre [(not (nil? feed-key)) (>= since 0) (pos? until)]}
     (redis/zrangebyscore feed-key since until "WITHSCORES"))
  ([feed-key since] (feed-load-cmd feed-key since (now)))
  ([feed-key] (feed-load-cmd feed-key 0)))

(defn load-feed
  [redii feed-key since until]
  (feed/with-connection-for-feed redii feed-key
    [connection]
    (feed/parse-stories-and-scores
     (redis/with-connection connection
       (feed-load-cmd feed-key since until)))))

(defmulti add-to-listing-digest :type)

(defmethod add-to-listing-digest "listing_multi_action" [current story]
  {:pre [(= (:listing_id current) (:listing_id story))]}
  (if (= (:actor_id current) (:actor_id story))
    (assoc current
      :types (distinct (conj (:types current) (:type story)))
      :score (:score story))
    (story/multi-actor-multi-action-digest
     (:listing_id current)
     (reduce (fn [h type] (assoc h type (conj (h type) (:actor_id current))))
             {(:type story) [(:actor_id story)]}
             (:types current))
     (:score story))))

(defmethod add-to-listing-digest "listing_multi_actor" [current story]
   {:pre [(= (:listing_id current) (:listing_id story))]}
   (if (= (:type story) (:action current))
     (assoc current
       :actor_ids (distinct (conj (:actor_ids current) (:actor_id story)))
       :score (:score story))
     (story/multi-actor-multi-action-digest
      (:listing_id current)
      {(:action current) (:actor_ids current)
       (:type story) [(:actor_id story)]}
      (:score story))))

(defmethod add-to-listing-digest "listing_multi_actor_multi_action" [current story]
  {:pre [(= (:listing_id current) (:listing_id story))]}
  (let [path [:types (:type story)]]
    (-> current
        (assoc-in path (distinct (conj (get-in current path) (:actor_id story))))
        (assoc :score (:score story)))))

(defn- listing-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :actors (conj (:actors m) (:actor_id story))
              :types (conj (:types m) (:type story))))
          {:actors #{} :types #{}}
          stories))

(defn- mama-types
  [stories]
  (reduce (fn [m story] (assoc m (:type story) (conj (m (:type story)) (:actor_id story))))
          {} stories))

(defn maybe-create-new-listing-digest
  "all stories MUST have the same listing id"
  [current story]
  (let [stories (conj current story)
        index (listing-digest-index-for-stories stories)
        listing-id (:listing_id story)]
    (case [(> (count (:actors index)) 1) (> (count (:types index)) 1)]
      [true true] (story/multi-actor-multi-action-digest
                   listing-id (mama-types stories) (:score story))
      [true false] (story/multi-actor-digest listing-id (:type story) (vec (:actors index)) (:score story))
      [false true] (story/multi-action-digest listing-id (:actor_id story) (vec (:types index)) (:score story))
      [false false] stories)))

(defn- classify-current
  [current]
  (when current
    (if (story/digest-story? current)
      :digest
      (if (set? current)
        :set
        (throw (Exception. (str "corrupt cache! contains " current)))))))

(defn- listings-story-aggregator [current story]
  {:pre [(not (nil? story)) (not (nil? (:listing_id story)))]}
  (case [(classify-current current) (boolean (story/digest-story? story))]
    [nil false] #{story}
    [:digest false] (add-to-listing-digest current story)
    [:set false] (maybe-create-new-listing-digest current story)

    ;; happens when loading a predigested feed
    [nil true] story

    ;; pathological states, try to repair
    [:digest true] (do (log/warn "duplicate digest stories! " current story "using newer") story)
    [:set true] (do (log/warn "undigested and digested coexist! " current story "using digest") story)))

(defn add-story-to-listings-index
  [digesting-index story]
  (if (:listing_id story)
    (let [path [:listings (:listing_id story)]]
      (assoc-in digesting-index path
                (listings-story-aggregator (get-in digesting-index path) story)))
    digesting-index))

(defn add-to-actor-digest [current story]
  {:pre [(= (:actor_id current) (:actor_id story))]}
  (assoc current
    :listing_ids (distinct (conj (:listing_ids current) (:listing_id story)))
    :score (:score story)))

(defn- actor-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :listings (conj (:listings m) (:listing_id story))))
          {:listings #{}}
          stories))

(defn maybe-create-new-actor-digest
  "all stories MUST have the same actor id"
  [current story]
  (let [stories (conj current story)
        index (actor-digest-index-for-stories stories)
        actor-id (:actor_id story)]
    (if (> (count (:listings index)) 15) ;;XXX: constant
      (story/multi-listing-digest actor-id (:type story) (vec (:listings index)) (:score story))
      stories)))

(defn actor-story-aggregator
  [current story]
  (case [(classify-current current) (boolean (story/digest-story? story))]
    [nil false] #{story}
    [:digest false] (add-to-actor-digest current story)
    [:set false] (maybe-create-new-actor-digest current story)

    ;; happens when loading a predigested feed
    [nil true] story

    ;; pathological states, try to repair
    [:digest true] (do (log/warn "duplicate digest stories! " current story "using newer") story)
    [:set true] (do (log/warn "undigested and digested coexist! " current story "using digest") story)))

(defn add-story-to-actors-index
  [digesting-index story]
  (if (:actor_id story)
    (let [path [:actors (:actor_id story)]]
      (assoc-in digesting-index path
                (actor-story-aggregator (get-in digesting-index path) story)))
    digesting-index))

(defn add-story
  [digesting-index story]
  (-> digesting-index
      (add-story-to-listings-index story)
      (add-story-to-actors-index story)))

(defn index-predigested-feed
  [feed]
  (reduce add-story {:listings {} :actors {}} feed))

(defn- bucket-story
  [m story]
  (if (map? story)
    (assoc m :digest (conj (:digest m) story))
    (assoc m :single (set/union (:single m) story))))

(defn feed-from-index
  [digesting-index]
  (let [bucketed-stories
        [(reduce bucket-story {} (vals (:listings digesting-index)))
         (reduce bucket-story {} (vals (:actors digesting-index)))]]
    (concat (apply set/union (map :digest bucketed-stories))
            (apply set/intersection (map :single bucketed-stories)))))

(comment
  (let [f (load-feed (:development config/redii) "magd:f:u:47:c" 0 (now))
        f2 (feed-from-index (index-predigested-feed f))]

    (count f2))


  )

