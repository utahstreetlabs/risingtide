(ns risingtide.test.digest
  (:use risingtide.digest
        risingtide.test
        [risingtide.core :only [env]])
  (:require
   [risingtide.stories :as story])
  (:use [midje.sweet]))

(defmacro defmany
  [& forms]
  `(do
     ~@(for [[name body] (partition 2 forms)]
         `(def ~name ~body))))

(defn- us
  "remove scores, encodings to make it easier to test for equality"
  [story]
  (dissoc story :score :encoded))

(defn us-stories
  "remove scores, encodings from second element in a tuple to make it easier to test for equality"
  [[a b]]
  [a (map us b)])

(defmany
  jim 1 jon 2 rob 3

  ;; listing ids
  hams 11 bacon 12

  ;; stories
  jim-activated-hams (listing-activated jim hams) jim-liked-hams (listing-liked jim hams)
  jim-shared-hams (listing-shared jim hams) jim-sold-hams (listing-sold jim hams)
  jim-commented-hams (listing-commented jim hams)

  jon-activated-hams (listing-activated jon hams) jon-liked-hams (listing-liked jon hams)
  jon-shared-hams (listing-shared jon hams) jon-sold-hams (listing-sold jon hams)
  jon-commented-hams (listing-commented jon hams)

  rob-activated-hams (listing-activated rob hams) rob-liked-hams (listing-liked rob hams)
  rob-shared-hams (listing-shared rob hams) rob-sold-hams (listing-sold rob hams)
  rob-commented-hams (listing-commented rob hams)

  jim-activated-bacon (listing-activated jim bacon) jim-liked-bacon (listing-liked jim bacon)
  jim-shared-bacon (listing-shared jim bacon) jim-sold-bacon (listing-sold jim bacon)
  jim-commented-bacon (listing-commented jim bacon)

  jon-activated-bacon (listing-activated jon bacon) jon-liked-bacon (listing-liked jon bacon)
  jon-shared-bacon (listing-shared jon bacon) jon-sold-bacon (listing-sold jon bacon)
  jon-commented-bacon (listing-commented jon bacon))

(fact
  (let [story {:type :made-bacon :listing_id 1 :actor_id 2}]
    (digest-story {} story) => {:listings {1 {:made-bacon {2 story}}}
                                :actors {[2 :made-bacon] {1 story}}}))

(fact "single listing digest stories are classified properly"
  (tabular (us-stories (classify-single-listing-digest-story ?input)) => (us-stories ?result)
           ?input ?result
           [1 {:activated {2 jim-activated-hams}}]
           [:single [jim-activated-hams]]

           [1 {:liked {2 jim-liked-hams 3 jon-liked-hams}}]
           [:digest [(story/multi-actor-digest 1 :liked [2 3])]]

           [1 {:liked {2 jim-liked-hams} :activated {2 jon-activated-hams}}]
           [:digest [(story/multi-action-digest 1 2 [:activated :liked ])]]

           [1 {:liked {2 jim-liked-hams 3 jon-liked-hams} :shared {2 jim-shared-hams} :activated {4 rob-activated-hams}}]
           [:digest [(story/multi-actor-multi-action-digest 1 {:activated [4] :shared [2] :liked [2 3]})]]))

(fact "multi-listing digest stories are classified properly"
  (tabular (us-stories (classify-single-actor-digest-story ?input)) => (us-stories ?result)
           ?input ?result
           [[1 :liked] {2 jim-liked-hams 3 jim-liked-bacon}]
           [:single [jim-liked-hams jim-liked-bacon]]

           [[1 :liked] (reduce (fn [m i] (assoc m i (listing-liked 1 i))) {} (range 0 16))]
           [:digest [(story/multi-listing-digest 1 :liked (range 0 16))]]))

(fact
  (reduce digest-story {} [jim-activated-hams jim-liked-hams jim-shared-hams]) =>
  {:listings
   {hams {"listing_activated" {jim jim-activated-hams}
          "listing_liked" {jim jim-liked-hams}
          "listing_shared" {jim jim-shared-hams}}}
   :actors
   {[jim "listing_activated"] {hams jim-activated-hams}
    [jim "listing_liked"] {hams jim-liked-hams}
    [jim "listing_shared"] {hams jim-shared-hams}}})

(tabular
 (fact (map us (digest ?feed)) => (map us ?digested))
 ?feed ?digested

 ;; one user - one listing - multiple actions
 [jim-activated-hams jim-liked-hams jim-shared-hams]
 ;; digests to
 [(story/multi-action-digest hams jim [ "listing_activated" "listing_liked" "listing_shared" ])]

 [jim-activated-hams jim-liked-hams jim-shared-hams jim-liked-bacon]
 ;; digests to
 [(story/multi-action-digest hams jim [ "listing_activated" "listing_liked" "listing_shared"]) jim-liked-bacon]

 ;; multiple users - one listing - one action
 [jon-activated-hams jim-shared-bacon jim-activated-hams]
 ;; digests to
 [(story/multi-actor-digest hams "listing_activated" [jim jon]) jim-shared-bacon]

 ;; multiple users - one listing - multiple actions
 [jon-activated-hams jim-shared-hams jim-activated-hams jon-shared-hams]
 ;; digests to
 [(story/multi-actor-multi-action-digest hams {"listing_activated" [jim jon] "listing_shared" [jim jon]})]

 (map #(listing-activated jim %) (range 0 16))
 ;; ;; digests to
 [(story/multi-listing-digest jim "listing_activated" (range 0 16))]

 ;;TODO: network feed 1 user - multiple other users - same action
 )


