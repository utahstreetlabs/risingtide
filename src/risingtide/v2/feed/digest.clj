(ns risingtide.v2.feed.digest
  (require [risingtide.v2.story :refer [StoryDigest action-for score with-score] :as story]
           [risingtide.v2.feed :refer [Feed] :as feed]
           [risingtide.config :as config]
           [clojure.tools.logging :as log]
           [clojure.set :as set])
  (import [risingtide.v2.story TagLikedStory ListingLikedStory ListingCommentedStory ListingActivatedStory ListingSoldStory ListingSharedStory MultiActorStory MultiActionStory MultiActorMultiActionStory MultiListingStory]))

(defn- listing-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :actors (conj (:actors m) (:actor-id story))
              :actions (conj (:actions m) (action-for story))))
          {:actors #{} :actions #{}}
          stories))

(defn- mama-actions
  [stories]
  (reduce (fn [m story] (assoc m (action-for story) (set (conj (m (action-for story)) (:actor-id story)))))
          {} stories))

(deftype ListingStorySet [stories]
  StoryDigest
  (add [this story]
    ;; prereqs: story must match listing id of this
    (let [new-stories (set (conj stories story))
          index (listing-digest-index-for-stories new-stories)
          listing-id (:listing-id story)]
      (case [(> (count (:actors index)) 1) (> (count (:actions index)) 1)]
        [true true] (with-score (story/->MultiActorMultiActionStory
                                 listing-id (mama-actions new-stories))
                       (score story))
        [true false] (with-score (story/->MultiActorStory listing-id (action-for story) (set (:actors index))) (score story))
        [false true] (with-score (story/->MultiActionStory listing-id (:actor-id story) (set (:actions index))) (score story))
        [false false] (ListingStorySet. new-stories)))))

(deftype ActorStorySet [stories]
  StoryDigest
  (add [this story]
    ;; prereqs: story must match actor id, action of this
    (let [new-stories (set (conj stories story))
          listing-ids (set (map :listing-id new-stories))]
      (if (>= (count listing-ids) config/single-actor-digest-story-min)
        (with-score (story/->MultiListingStory (:actor-id story) (action-for story) listing-ids)  (score story))
        (ActorStorySet. new-stories)))))

;;;;;;; digest indexing ;;;;;;;
;;; add a big ass comment here explaining digest indexing

(defn- listing-index-path [story]
  [:listings (:listing-id story)])

(defn- actor-index-path [story]
  [:actors (:actor-id story) (action-for story)])

(defn- add-story [existing-digest story init-digest]
  "Given the value of an existing digest for the given story,
either add the story to the digest or return a new digest object
containing the story by passing the story to init-digest."
  (if existing-digest
    (story/add existing-digest story)
    (init-digest #{story})))

(defn- add-digest [existing-digest new-digest]
  "Given the value of an existing digest and a new digest value, reconcile the two.
This should only happen when loading digest stories from disk"
  (when existing-digest (log/warn "trying to add digest story to index but something's already here: " existing-digest " so I'll use the newer story: " new-digest))
  new-digest)

(defprotocol Indexable
  (index [story index]
    "Given a story and an index, update the index appropriately."))

(defn add-to-index [idx story]
  (index story idx))

;; extend Indexable to the story classes

(defn- index-with [index & args]
  (doseq [type args]
    (extend type Indexable
            {:index index})))

(index-with
 (fn [story index]
   (-> index
       (update-in (listing-index-path story) add-story story ->ListingStorySet)
       (update-in (actor-index-path story) add-story story ->ActorStorySet)))
 ListingLikedStory ListingCommentedStory ListingActivatedStory ListingSoldStory ListingSharedStory)

(index-with
 (fn [story index]
   (assoc index :nodigest (cons story (:nodigest index))))
 TagLikedStory)

(index-with
 (fn [story index]
   (update-in (listing-index-path story) add-digest story))
 MultiActorMultiActionStory MultiActionStory MultiActorStory)

(index-with
 (fn [story index]
   (update-in (actor-index-path story) add-digest story))
 MultiListingStory)

;;;; ToStories - converting a digesting index to a feed ;;;;

(defprotocol ToStories
  (to-stories [leaf]
    "Given a leaf in the index, return a tuple of sets of single stories and digest stories
to be inserted into the feed."))

;; extend ToStories to the story classes

(defn- to-stories-with [to-stories & args]
  (doseq [type args]
    (extend type ToStories
            {:to-stories to-stories})))

(to-stories-with
 (fn [leaf] [#{leaf} #{}])
 MultiActorMultiActionStory MultiActionStory MultiActorStory MultiListingStory)

(to-stories-with
 (fn [leaf] [#{} (.stories leaf)])
 ListingStorySet ActorStorySet)

(defn- reduce-digests [digests]
  "Given a set of digest index leaves, return a tuple of stories and digest stories from those leaves."
  (reduce (fn [[digest-m single-m] [digest single]] [(set/union digest-m digest) (set/union single-m single)])
          [] (map to-stories digests)))

(defn feed-from-index
  [digesting-index]
  (let [[listing-digests listing-stories] (reduce-digests (vals (:listings digesting-index)))
        [actor-digests actor-stories] (reduce-digests (apply concat (map vals (vals (:actors digesting-index)))))]
    (concat (:nodigest digesting-index)
            ;; union all digest stories
            (set/union listing-digests actor-digests)
            ;; take the intersection of single story sets to ensure we only get stories
            ;; that are not in any digest stories
            (set/intersection listing-stories actor-stories))))

(defn new-index [] {})

(deftype DigestFeed [story-index]
  Feed
  (add [this story] (DigestFeed. (index story story-index)))
  clojure.lang.Seqable
  (seq [this] (feed-from-index story-index)))

(defn new-digest-feed
  []
  (->DigestFeed (new-index)))


