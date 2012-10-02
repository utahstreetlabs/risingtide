(ns risingtide.v2.story)

(defprotocol Story
  (to-json [this]))

(defrecord TagLikedStory [action actor-id tag-id score])
(defrecord ListingLikedStory [action actor-id listing-id tag-ids feed score])
(defrecord ListingCommentedStory [action actor-id listing-id tag-ids text feed score])
(defrecord ListingActivatedStory [action actor-id listing-id tag-ids feed score])
(defrecord ListingSoldStory [action actor-id listing-id tag-ids buyer-id feed score])
(defrecord ListingSharedStory [action actor-id listing-id tag-ids network feed score])

(defprotocol StoryDigest
  (add [this story] "add a new story to this digest story"))

(defrecord MultiActorMultiActionStory [listing-id actions score]
  StoryDigest
  (add [this story]
    (let [path [:actions (:actions story)]]
      (assoc (assoc-in this path (distinct (conj (get-in this path) (:actor-id story))))
        :score (:score story)))))

(defrecord MultiActionStory [listing-id actor-id actions score]
  StoryDigest
  (add [this story]
    (if (= actor-id (:actor-id story))
      (assoc this
        :actions (distinct (conj actions (:action story)))
        :score (:score story))
      (->MultiActorMultiActionStory
       listing-id
       (reduce (fn [h action] (assoc h action (conj (h action) actor-id)))
               {(:action story) [(:actor-id story)]}
               actions)
       (:score story)))))

(defrecord MultiActorStory [listing-id action actor-ids score]
  StoryDigest
  (add [this story]
   (if (= (:action story) action)
     (assoc this
       :actor-ids (distinct (conj actor-ids (:actor-id story)))
       :score (:score story))
     (->MultiActorMultiActionStory
      listing-id
      {action actor-ids
       (:action story) [(:actor-id story)]}
      (:score story)))))

(defrecord MultiListingStory [actor-id action listing-ids score]
  StoryDigest
  (add [this story]
    (assoc this
      :listing-ids (distinct (conj listing-ids (:listing-id story)))
      :score (:score story))))
