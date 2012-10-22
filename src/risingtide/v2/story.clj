(ns risingtide.v2.story)

(defprotocol Action
  (action [story] "return the action symbol for this story"))

(defrecord TagLikedStory [actor-id tag-id])
(defrecord ListingLikedStory [actor-id listing-id tag-ids feed])
(defrecord ListingCommentedStory [actor-id listing-id tag-ids text feed])
(defrecord ListingActivatedStory [actor-id listing-id tag-ids feed])
(defrecord ListingSoldStory [actor-id listing-id tag-ids buyer-id feed])
(defrecord ListingSharedStory [actor-id listing-id tag-ids network feed])

(defn score [story]
  (:score (meta story)))

(defn with-score [story score]
  (with-meta story (assoc (meta story) :score score)))

(defprotocol Action (action-for [story] "return the action symbol for this story"))
(extend-protocol Action
  TagLikedStory (action-for [_] :tag_liked)
  ListingLikedStory (action-for [_] :listing_liked)
  ListingCommentedStory (action-for [_] :listing_commented)
  ListingActivatedStory (action-for [_] :listing_activated)
  ListingSoldStory (action-for [_] :listing_sold)
  ListingSharedStory (action-for [_] :listing_shared))

(def story-factory-for
  {:tag_liked map->TagLikedStory
   :listing_liked map->ListingLikedStory
   :listing_commented map->ListingCommentedStory
   :listing_activated map->ListingActivatedStory
   :listing_sold map->ListingSoldStory
   :listing_shared map->ListingSharedStory})

;;; Story Digests

(defprotocol StoryDigest
  (add [this story] "add a new story to this digest story"))

(defrecord MultiActorMultiActionStory [listing-id actions]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (let [path [:actions (action-for story)]]
      (with-score (assoc-in this path (set (conj (get-in this path) (:actor-id story))))
        (score story)))))

(defrecord MultiActionStory [listing-id actor-id actions]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (with-score
     (if (= actor-id (:actor-id story))
       (assoc this :actions (set (conj actions (action-for story))))
       (->MultiActorMultiActionStory
        listing-id
        (reduce (fn [h action] (assoc h action (set (conj (h action) actor-id))))
                {(action-for story) #{(:actor-id story)}}
                actions)))
     (score story))))

(defrecord MultiActorStory [listing-id action actor-ids]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same listing-id
    (with-score
     (if (= (action-for story) action)
       (assoc this :actor-ids (set (conj actor-ids (:actor-id story))))
       (->MultiActorMultiActionStory
        listing-id
        {action actor-ids
         (action-for story) #{(:actor-id story)}}))
     (score story))))

(defrecord MultiListingStory [actor-id action listing-ids]
  StoryDigest
  (add [this story]
    ;; XXX: prereqs must have same actor-id, action
    (with-score
     (assoc this
       :listing-ids (set (conj listing-ids (:listing-id story))))
     (score story))))

