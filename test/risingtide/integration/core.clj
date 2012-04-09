(ns risingtide.integration.core
  (:use risingtide.core
        risingtide.integration.support
        risingtide.test)
  (:use [midje.sweet])
  (:require [risingtide.stories :as story]))

(background
 (before :facts (clear-redis!))
 (before :facts (clear-digesting-cache!)))

(fact "initial feed builds bring in old stories"
  (on-copious
   (jim activates bacon)
   (jim likes ham)
   (jim likes toast)
   (jim shares toast)
   (jim likes omelettes {:feed ["ev"]})
   (jim joins)
   (jim follows jon))
  (clear-digesting-cache!)
  (on-copious
   (rob interested-in-user jim)
   (rob builds-feeds))

  (feed-for-rob :card) => (encoded-feed
                           (listing-activated jim bacon)
                           (listing-liked jim ham)
                           (story/multi-action-digest toast jim ["listing_liked" "listing_shared"]))

  (feed-for-rob :network) => (encoded-feed
                              (user-joined jim)
                              (user-followed jim jon))

  (everything-feed) => (encoded-feed
                        (listing-activated jim bacon)
                        (listing-liked jim ham)
                        (story/multi-action-digest toast jim ["listing_liked" "listing_shared"])
                        (listing-liked jim omelettes {:feed ["ev"]})))

(fact "network feeds are generated correctly"
    (on-copious
     (rob interested-in-user jim)
     (rob interested-in-user jon)
     (jim joins)
     (jim follows jon)
     (jim invites mark-z)
     (jon piles-on mark-z))

    (feed-for-rob :network) => (encoded-feed
                                (user-joined jim)
                                (user-followed jim jon)
                                (user-invited jim mark-z)
                                (user-piled-on jon mark-z))

    (feed-for-rob :card) => []

    (everything-feed) => [])

(fact "multiple actions by an interesting user are digested"
  (on-copious
   (rob interested-in-user jim)
   (jim activates bacon)
   (jim likes bacon)
   ;; stuff that shouldn't matter
   (jon likes bacon))

  (feed-for-rob :card) => (encoded-feed
                           (story/multi-action-digest bacon jim ["listing_activated" "listing_liked"]))
  (feed-for-jim :card) => empty-feed)

(fact "multiple actions by a multiple interesting users are digested"
  (on-copious
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim activates bacon)
   (jim likes bacon)
   (jon likes bacon)
   ;; stuff that shouldn't matter
   (bcm shares bacon))

  (feed-for-rob :card) => (encoded-feed (story/multi-actor-multi-action-digest
                                         bacon
                                         {"listing_liked" [jim jon] "listing_activated" [jim]}))
  (feed-for-jim :card) => empty-feed)

(fact "multiple users performing the same action are digested"
  (on-copious
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim likes bacon)
   (jon likes bacon)
   ;; stuff that shouldn't matter
   (bcm likes bacon))

  (feed-for-rob :card) => (encoded-feed (story/multi-actor-digest
                                         bacon "listing_liked" [jim jon]))
  (feed-for-jim :card) => empty-feed)

(fact "digest stories coexist peacefully with other stories"
  (on-copious
   (jim likes ham)
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim likes bacon)
   (jon likes bacon)
   (jon likes eggs)
   ;; stuff that shouldn't matter
   (bcm likes bacon))

  (feed-for-rob :card) => (encoded-feed
                           (listing-liked jim ham)
                           (story/multi-actor-digest bacon "listing_liked" [jim jon])
                           (listing-liked jon eggs))
  (feed-for-jim :card) => empty-feed)

(fact "the everything feed contains (allthethings)"
  (on-copious
   (jim likes ham)
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim likes bacon)
   (jon likes bacon)
   (jon likes eggs)
   (bcm likes bacon)
   (jim likes toast {:feed "ylf"})
   (dave shares muffins)) ;; NOTE THAT THIS HAS NEVER HAPPENED >:o

  (everything-feed) => (encoded-feed
                        (listing-liked jim ham)
                        (listing-liked jon eggs)
                        (story/multi-actor-digest bacon "listing_liked" [jim jon bcm])
                        (listing-shared dave muffins))
  (feed-for-rob :card) => (encoded-feed
                           (listing-liked jim ham)
                           (story/multi-actor-digest bacon "listing_liked" [jim jon])
                           (listing-liked jon eggs)
                           (listing-liked jim toast {:feed "ylf"})))
