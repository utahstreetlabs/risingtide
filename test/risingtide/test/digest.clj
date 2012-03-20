(ns risingtide.test.stories
  (:use risingtide.digest
        risingtide.test
        [risingtide.core :only [env]])
  (:use [midje.sweet]))

(let [;; user ids
      jim 1 jon 2

      ;; listing ids
      hams 11 bacon 12

      ;; stories
      jim-activated-hams (listing-activated jim hams) jim-liked-hams (listing-liked jim hams)
      jim-shared-hams (listing-shared jim hams) jim-sold-hams (listing-sold jim hams)
      jim-commented-hams (listing-commented jim hams)

      jon-activated-hams (listing-activated jon hams) jon-liked-hams (listing-liked jon hams)
      jon-shared-hams (listing-shared jon hams) jon-sold-hams (listing-sold jon hams)
      jon-commented-hams (listing-commented jon hams)

      jim-activated-bacon (listing-activated jim bacon) jim-liked-bacon (listing-liked jim bacon)
      jim-shared-bacon (listing-shared jim bacon) jim-sold-bacon (listing-sold jim bacon)
      jim-commented-bacon (listing-commented jim bacon)

      jon-activated-bacon (listing-activated jon bacon) jon-liked-bacon (listing-liked jon bacon)
      jon-shared-bacon (listing-shared jon bacon) jon-sold-bacon (listing-sold jon bacon)
      jon-commented-bacon (listing-commented jon bacon)
      ]

  (fact
    (let [story {:type :made-bacon :listing_id 1 :actor_id 2}]
      (digest-story {} story) => {:listings {1 {:made-bacon {2 story}}}}))

  (fact
    (single-listing-digest-story [1 {:made-bacon {2 :a}}]) => :a

    (single-listing-digest-story [1 {:made-bacon {2 :a 3 :b}}]) =>
    (multi-actor-digest-story 1 :made-bacon [2 3])

    (single-listing-digest-story [1 {:made-bacon {2 :a} :made-donuts {2 :b}}]) =>
    (multi-action-digest-story 1 2 [:made-bacon :made-donuts])


    (single-listing-digest-story [1 {:made-bacon {2 :a 3 :b} :made-donuts {2 :d} :made-eggs {4 :e}}]) =>
    (multi-actor-multi-action-digest-story 1 {:made-bacon [2 3] :made-donuts [2] :made-eggs [4]}))

  (fact
    (reduce digest-story {} [jim-activated-hams jim-liked-hams jim-shared-hams]) =>
    {:listings
     {hams {"listing_activated" {jim jim-activated-hams}
            "listing_liked" {jim jim-liked-hams}
            "listing_shared" {jim jim-shared-hams}}}})

  (tabular
   (fact (digest ?feed) => ?digested)
   ?feed ?digested


   ;; one user - one listing - multiple actions
   [jim-activated-hams jim-liked-hams jim-shared-hams]
   ;; digests to
   [(multi-action-digest-story hams jim ["listing_shared" "listing_liked" "listing_activated"])]

   [jim-activated-hams jim-liked-hams jim-shared-hams jim-liked-bacon]
   ;; digests to
   [jim-liked-bacon (multi-action-digest-story hams jim ["listing_shared" "listing_liked" "listing_activated"])]


   ;; multiple users - one listing - one action
   [jon-activated-hams jim-shared-bacon jim-activated-hams]
   ;; digests to
   [jim-shared-bacon (multi-actor-digest-story hams "listing_activated" [jim jon])]


   ;; multiple users - one listing - multiple actions
   [jon-activated-hams jim-shared-hams jim-activated-hams jon-shared-hams]
   ;; digests to
   [(multi-actor-multi-action-digest-story hams {"listing_activated" [jim jon] "listing_shared" [jon jim]})]

   ;;TODO: one user - multiple listings - same action
   ;; [jim-activated-hams jim-activated-bacon]
   ;; ;; digests to
   ;; [(multi-listing-digest-story jim "listing_activated" hams bacon)]

   ;;TODO: network feed 1 user - multiple other users - same action
   )
  )

