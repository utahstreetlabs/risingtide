(ns risingtide.integration.core
  (:use risingtide.core
        risingtide.integration.support
        risingtide.test)
  (:use [midje.sweet])
  (:require [risingtide
             [stories :as story]
             [feed :as feed]
             [shard :as shard]
             [digest :as digest]
             [persist :as persist]
             [key :as key]]))

(test-background
 (before :facts (clear-redis!))
 (before :facts (clear-digest-cache!))
 (before :facts (clear-migrations!)))


(fact "initial feed builds bring in old stories"
  (on-copious
   (jim activates bacon)
   (jim likes ham)
   (jim likes toast)
   (jim shares toast)
   (jim likes omelettes {:feed ["ev"]})
   (jim joins)
   (jim follows jon)
   (conn write!)
   (rob interested-in-user jim)
   (rob builds-feeds))

  (feed-for-rob :card) => (encoded-feed
                           (listing-activated jim bacon)
                           (listing-liked jim ham)
                           (story/multi-action-digest toast jim ["listing_shared" "listing_liked"]))

  (feed-for-rob :network) => (encoded-feed
                              (user-joined jim)
                              (user-followed jim jon))

  (everything-feed) => (encoded-feed
                        (listing-activated jim bacon)
                        (listing-liked jim ham)
                        (story/multi-action-digest toast jim ["listing_shared" "listing_liked"])
                        (listing-liked jim omelettes {:feed ["ev"]})))

(fact "adding interests backfills the feed"
  (on-copious
   (jim activates bacon)
   (jim likes ham)
   (jim likes toast)
   (jim shares toast)
   (jim likes omelettes {:feed ["ev"]})
   (jim joins)
   (jim follows jon)
   (rob interested-in-user jim))

  (feed-for-rob :card) => (encoded-feed
                           (listing-activated jim bacon)
                           (listing-liked jim ham)
                           (story/multi-action-digest toast jim ["listing_shared" "listing_liked"]))

  (feed-for-rob :network) => (encoded-feed
                              (user-joined jim)
                              (user-followed jim jon))

  (everything-feed) => (encoded-feed
                        (listing-activated jim bacon)
                        (listing-liked jim ham)
                        (story/multi-action-digest toast jim ["listing_shared" "listing_liked"])
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
                           (story/multi-action-digest bacon jim ["listing_liked" "listing_activated"]))
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
                                         {"listing_liked" [jon jim] "listing_activated" [jim]}))
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

(fact "one user performing the same action on more than 15 listings is digested"
  (on-copious
   (rob interested-in-user jim)
   (jim activates-many-listings (range 0 16))
   ;; stuff that shouldn't matter
   (bcm likes bacon))

  (feed-for-rob :card) => (encoded-feed (story/multi-listing-digest jim "listing_activated" (range 0 16))))

(fact "multi-listing and single-listing digest stories coexist and sometimes carry redundant information"
  (on-copious
   (rob interested-in-user jim)
   (jim activates-many-listings (range 0 16))
   (jim likes 1)
   ;; stuff that shouldn't matter
   (bcm likes bacon))

  (feed-for-rob :card) => (encoded-feed (story/multi-listing-digest jim "listing_activated" (range 0 16))
                                        (story/multi-action-digest 1 jim ["listing_liked" "listing_activated"])))

(fact "digest stories coexist peacefully with other stories"
  (on-copious
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim likes ham)
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
   (rob interested-in-user jim)
   (rob interested-in-user jon)
   (jim likes ham)
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

(fact "card feeds are truncated when a new card story is added"
  (binding [persist/*max-card-feed-size* 5]
    (on-copious
     (rob interested-in-user jim)
     (jim likes bacon)
     (jim likes eggs)
     (jim likes toast)
     (jim likes muffins)
     (jim likes ham)
     (jim likes omelettes))

    (feed-for-rob :card) => (encoded-feed
                             (listing-liked jim eggs)
                             (listing-liked jim toast)
                             (listing-liked jim muffins)
                             (listing-liked jim ham)
                             (listing-liked jim omelettes))))

(fact "network feeds are truncated when a new network story is added"
  (binding [persist/*max-network-feed-size* 5]
    (on-copious
     (rob interested-in-user jim)
     (jim follows jon)
     (jim follows bcm)
     (jim follows dave)
     (jim follows cutter)
     (jim follows kaitlyn)
     (jim follows courtney))

    (feed-for-rob :network) => (encoded-feed
                                (user-followed jim bcm)
                                (user-followed jim dave)
                                (user-followed jim cutter)
                                (user-followed jim kaitlyn)
                                (user-followed jim courtney))))

(fact "digest cards aren't duplicated when they are the oldest thing in the feed"
  (on-copious
   (rob interested-in-user jim)
   (jim activates-many-listings (range 0 16))
   (conn write!)
   (jim activates 16))

  (feed-for-rob :card) =>
  (encoded-feed (story/multi-listing-digest jim "listing_activated" (range 0 17))))

(fact "migration moves a feed from one redis to another"
  (on-copious
   (rob interested-in-user jim)
   (jim activates bacon)
   (jim likes ham))

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed)

  (digest/migrate! conn (key/user-feed rob :card) "2")

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed)

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (on-copious
   (jim shares toast))

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed)

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham)
   (listing-shared jim toast)))

(fact "mid-migration the feed is in two places"
  (on-copious
   (rob interested-in-user jim)
   (jim activates bacon)
   (jim likes ham))

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed)

  (digest/initiate-migration! (key/user-feed rob :card) "2")
  (write! conn)

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (on-copious
   (jim shares toast))

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham)
   (listing-shared jim toast))

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham)
   (listing-shared jim toast)))

(fact "stories are migrated even though they aren't in the digest cache"
  (on-copious
   (rob interested-in-user jim)
   (jim activates bacon)
   (jim likes ham))

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham))

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed)

  (clear-digest-cache!)
  (digest/migrate! conn (key/user-feed rob :card) "2")

  (feed-on (:card-feeds-1 conn) rob :card) =>
  (encoded-feed)

  (feed-on (:card-feeds-2 conn) rob :card) =>
  (encoded-feed
   (listing-activated jim bacon)
   (listing-liked jim ham)))
