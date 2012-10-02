(ns risingtide.v2.feed.digest
  (require [risingtide.v2.story :refer [StoryDigest] :as story]
           [risingtide.config :as config]
           [clojure.tools.logging :as log])
  (import [risingtide.v2.story TagLikedStory ListingLikedStory ListingCommentedStory ListingActivatedStory ListingSoldStory ListingSharedStory MultiActorStory MultiActionStory MultiActorMultiActionStory MultiListingStory]))

(defn- listing-digest-index-for-stories [stories]
  (reduce (fn [m story]
            (assoc m
              :actors (conj (:actors m) (:actor-id story))
              :actions (conj (:actions m) (:action story))))
          {:actors #{} :actions #{}}
          stories))

(defn- mama-actions
  [stories]
  (reduce (fn [m story] (assoc m (:action story) (conj (m (:action story)) (:actor-id story))))
          {} stories))

(deftype ListingStorySet [stories]
  StoryDigest
  (add [this story]
    (let [new-stories (conj stories story)
          index (listing-digest-index-for-stories new-stories)
          listing-id (:listing-id story)]
      (case [(> (count (:actors index)) 1) (> (count (:actions index)) 1)]
        [true true] (story/->MultiActorMultiActionStory
                     listing-id (mama-actions new-stories) (:score story))
        [true false] (story/->MultiActorStory listing-id (:action story) (vec (:actors index)) (:score story))
        [false true] (story/->MultiActionStory listing-id (:actor-id story) (vec (:actions index)) (:score story))
        [false false] (ListingStorySet. new-stories)))))

(deftype ActorStorySet [stories]
  StoryDigest
  (add [this story]
    (let [new-stories (conj stories story)
          listing-ids (map :listing-id new-stories)]
      (if (> (count listing-ids) config/single-actor-digest-story-min)
        (story/->MultiListingStory (:actor-id story) (:action story) listing-ids (:score story))
        (ActorStorySet. new-stories)))))

;;; indexing

(defn- listing-index-path [story]
  [:listings (:listing-id story)])

(defn- actor-index-path [story]
  [:actors (:actor-id story) (:action story)])

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

(defn- index-actor-digest-story )

(defn- index-with [args]
  (doseq [type (butlast args)]
    (extend type Indexable
            {:index (last args)})))

(defprotocol Indexable
  (index [story index]
    "Given a story and an index, update the index appropriately."))

(index-with ListingLikedStory ListingCommentedStory ListingActivatedStory ListingSoldStory ListingSharedStory
  (fn [story index]
    (-> index
        (update-in (listing-index-path story) add-story story ->ListingStorySet)
        (update-in (actor-index-path story) add-story story ->ActorStorySet))))

(index-with TagLikedStory
  (fn [story index]
    (assoc index :nodigest (cons story (:nodigest index)))))

(index-with MultiActorMultiActionStory MultiActionStory MultiActorStory
  (fn [story index]
    (update-in (listing-index-path story) add-digest story)))

(index-with MultiListingStory
  (fn [story index]
    (update-in (actor-index-path story) add-digest story)))

(defn new-index [] {})
