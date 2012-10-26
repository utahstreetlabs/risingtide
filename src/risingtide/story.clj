(ns risingtide.story)

(defrecord TagLikedStory [actor-id tag-id])
(defrecord ListingLikedStory [actor-id listing-id tag-ids feed])
(defrecord ListingCommentedStory [actor-id listing-id tag-ids text feed])
(defrecord ListingActivatedStory [actor-id listing-id tag-ids feed])
(defrecord ListingSoldStory [actor-id listing-id tag-ids buyer-id feed])
(defrecord ListingSharedStory [actor-id listing-id tag-ids network feed])

(defn score [story]
  (:timestamp (meta story)))

(defn with-score [story score]
  (with-meta story (assoc (meta story) :timestamp score)))

(defprotocol TypeSym (type-sym [story] "return the type-sym symbol for this story"))

;;; Story Digests

(defprotocol StoryDigest
  (add [this story] "add a new story to this digest story"))

(defrecord MultiActorMultiActionStory [listing-id actions]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (let [path [:actions (type-sym story)]]
      (with-score (assoc-in this path (set (conj (get-in this path) (:actor-id story))))
        (score story)))))

(defrecord MultiActionStory [listing-id actor-id actions]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (with-score
     (if (= actor-id (:actor-id story))
       (assoc this :actions (set (conj actions (type-sym story))))
       (->MultiActorMultiActionStory
        listing-id
        (reduce (fn [h action] (assoc h action (set (conj (h action) actor-id))))
                {(type-sym story) #{(:actor-id story)}}
                actions)))
     (score story))))

(defrecord MultiActorStory [listing-id action actor-ids]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (with-score
     (if (= (type-sym story) action)
       (assoc this :actor-ids (set (conj actor-ids (:actor-id story))))
       (->MultiActorMultiActionStory
        listing-id
        {action actor-ids
         (type-sym story) #{(:actor-id story)}}))
     (score story))))

(defrecord MultiListingStory [actor-id action listing-ids]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same actor-id, action
    (with-score
     (assoc this
       :listing-ids (set (conj listing-ids (:listing-id story))))
     (score story))))

;;; Translating between type symbols used in JSON and types

(extend-protocol TypeSym
  TagLikedStory (type-sym [_] :tag_liked)
  ListingLikedStory (type-sym [_] :listing_liked)
  ListingCommentedStory (type-sym [_] :listing_commented)
  ListingActivatedStory (type-sym [_] :listing_activated)
  ListingSoldStory (type-sym [_] :listing_sold)
  ListingSharedStory (type-sym [_] :listing_shared)
  MultiActorStory (type-sym [_] :listing_multi_actor)
  MultiActionStory (type-sym [_] :listing_multi_action)
  MultiActorMultiActionStory (type-sym [_] :listing_multi_actor_multi_action)
  MultiListingStory (type-sym [_] :actor_multi_listing))

(def story-factory-for
  {:tag_liked map->TagLikedStory
   :listing_liked map->ListingLikedStory
   :listing_commented map->ListingCommentedStory
   :listing_activated map->ListingActivatedStory
   :listing_sold map->ListingSoldStory
   :listing_shared map->ListingSharedStory
   :listing_multi_actor map->MultiActorStory
   :listing_multi_action map->MultiActionStory
   :listing_multi_actor_multi_action map->MultiActorMultiActionStory
   :actor_multi_listing map->MultiListingStory})
