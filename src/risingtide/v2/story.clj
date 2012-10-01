(ns risingtide.v2.story)

(defprotocol Story
  (to-json [this]))

;;  :listing_activated, :listing_liked, :listing_commented, :listing_sold, :listing_shared, :tag_liked
;;
;;  inject_story(:tag_liked, liker.id, tag_id: tag.id)
;;  inject_listing_story(:listing_liked, liker.id, listing)
;;  inject_listing_story(:listing_commented, commenter.id, listing, text: comment['text'])
;;  inject_listing_story(:listing_activated, listing.seller_id, listing, {}, feed: [:ylf])
;;  inject_listing_story(:listing_sold, listing.seller_id, listing, buyer_id: listing.buyer_id)
;;  inject_listing_story(:listing_shared, sharer.id, listing, network: network)

(defrecord TagLikedStory [actor-id tag-id score])
(defrecord ListingLikedStory [actor-id listing-id tag-ids feed score])
(defrecord ListingCommentedStory [actor-id listing-id tag-ids text feed score])
(defrecord ListingActivatedStory [actor-id listing-id tag-ids feed score])
(defrecord ListingSoldStory [actor-id listing-id tag-ids buyer-id feed score])
(defrecord ListingSharedStory [actor-id listing-id tag-ids network feed score])

(defprotocol DigestStory
  (add [this story] "add a new story to this digest story"))

(defrecord MultiActorMultiActionStory [listing-id actions score]
  DigestStory
  (add [this story]
    (let [path [:types (:type story)]]
      (assoc (assoc-in this path (distinct (conj (get-in this path) (:actor-id story))))
        :score (:score story)))))

(defrecord MultiActionStory [listing-id actor-id actions score]
  DigestStory
  (add [this story]
    (if (= actor-id (:actor-id story))
      (assoc this
        :actions (distinct (conj actions (:type story)))
        :score (:score story))
      (->MultiActorMultiActionStory
       listing-id
       (reduce (fn [h type] (assoc h type (conj (h type) actor-id)))
               {(:type story) [(:actor-id story)]}
               actions)
       (:score story)))))

(defrecord MultiActorStory [listing-id action actor-ids score]
  DigestStory
  (add [this story]
   (if (= (:type story) action)
     (assoc this
       :actor-ids (distinct (conj actor-ids (:actor-id story)))
       :score (:score story))
     (->MultiActorMultiActionStory
      listing-id
      {action actor-ids
       (:type story) [(:actor-id story)]}
      (:score story)))))

(defrecord MultiListingDigestStory [actor-id action listing-ids score]
  DigestStory
  (add [this story]
    (assoc this
      :listing-ids (distinct (conj listing-ids (:listing-id story)))
      :score (:score story))))
