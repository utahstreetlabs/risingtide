(ns risingtide.test.digest
  (:use risingtide.dgest
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

(tabular
 (fact "add-to-listing-digest adds stories to multi-action digest properly"
   (us (add-to-listing-digest
        (story/multi-action-digest hams jim ["listing_liked" "listing_shared"])
        ?story)) => (us ?digest))
 ?story ?digest

 jim-commented-hams (story/multi-action-digest hams jim ["listing_liked" "listing_shared" "listing_commented"])

 jon-liked-hams (story/multi-actor-multi-action-digest hams {"listing_liked" [jon jim] "listing_shared" [jim]})

 jon-commented-hams (story/multi-actor-multi-action-digest hams {"listing_liked" [jim] "listing_shared" [jim] "listing_commented" [jon]}))

(tabular
 (fact "add-to-listing-digest adds stories to multi-actor digest properly"
   (us (add-to-listing-digest
        (story/multi-actor-digest hams "listing_liked" [jim jon])
        ?story)) => (us ?digest))

 ?story ?digest

 rob-liked-hams (story/multi-actor-digest hams "listing_liked" [jim jon rob])

 jim-shared-hams (story/multi-actor-multi-action-digest hams {"listing_liked" [jim jon] "listing_shared" [jim]})

 rob-shared-hams (story/multi-actor-multi-action-digest hams {"listing_liked" [jim jon] "listing_shared" [rob]})
 )

(tabular
 (fact "add-to-listing-digest adds stories to multi-actor-multi-listing digest properly"
   (us (add-to-listing-digest
        (story/multi-actor-multi-action-digest hams {"listing_liked" [jim] "listing_shared" [jon]}) ?story)) =>
        (us (story/multi-actor-multi-action-digest hams ?digest-types)))

 ?story ?digest-types

 rob-shared-hams {"listing_liked" [jim] "listing_shared" [jon rob]}

 rob-commented-hams {"listing_liked" [jim] "listing_shared" [jon] "listing_commented" [rob]}

 jim-liked-hams {"listing_liked" [jim] "listing_shared" [jon]})

(tabular
 (fact "maybe-create-new-listing-digest"
   (us (maybe-create-new-listing-digest ?stories ?new)) => (us ?digest))

 ?stories ?new ?digest

 #{jim-liked-hams} rob-liked-hams (story/multi-actor-digest hams "listing_liked" [jim rob])
 #{jim-liked-hams} jim-shared-hams (story/multi-action-digest hams jim ["listing_shared" "listing_liked"])
 #{jim-liked-hams} rob-shared-hams (story/multi-actor-multi-action-digest hams {"listing_liked" [jim] "listing_shared" [rob]}))

(tabular
 (fact "add-to-actor-digest adds stories to actor-digest stories"
   (us (add-to-actor-digest
        (story/multi-listing-digest jim "listing_liked" [hams]) ?story)) =>
        (us ?digest))

 ?story ?digest

 jim-liked-bacon (story/multi-listing-digest jim "listing_liked" [hams bacon]))

(tabular
 (fact "feed generation works"
   (map us (feed-from-index (index-predigested-feed ?undigested))) => (map us ?digested))
 ?undigested ?digested
 [jim-liked-hams]
  ;; digests to
 [jim-liked-hams]

 [jim-liked-hams rob-shared-hams]
 ;; digests to
 [(story/multi-actor-multi-action-digest hams {"listing_liked" [jim] "listing_shared" [rob]})]

 [jim-activated-hams jim-liked-hams jim-shared-hams]
 ;; digests to
 [(story/multi-action-digest hams jim ["listing_liked" "listing_activated" "listing_shared" ])]

 [jim-activated-hams jim-liked-hams jim-shared-hams jim-liked-bacon]
 ;; digests to
 [(story/multi-action-digest hams jim ["listing_liked" "listing_activated" "listing_shared"]) jim-liked-bacon]

 ;; multiple users - one listing - one action
 [jon-activated-hams jim-shared-bacon jim-activated-hams]
 ;; digests to
 [(story/multi-actor-digest hams "listing_activated" [jim jon]) jim-shared-bacon]

 ;; multiple users - one listing - multiple actions
 [jon-activated-hams jim-shared-hams jim-activated-hams jon-shared-hams]
 ;; digests to
 [(story/multi-actor-multi-action-digest hams {"listing_activated" [jim jon] "listing_shared" [jon jim]})]

 (map #(listing-activated jim %) (range 0 16))
 ;; ;; digests to
 [(story/multi-listing-digest jim "listing_activated" (range 0 16))])


(comment
  (feed-from-index (index-predigested-feed [jim-liked-hams]))

(fact "adding stuff to the feed cache works"
  (map #(add-story-to-feed-cache "testfeed" %))

  )
  (feed-from-index
   (binding [*cache-ttl* (* 5 24 60 60)]
      (add-story-to-feed-cache (:development risingtide.config/redii)
                               "magt:f:u:0:c" jim-liked-hams)))

)