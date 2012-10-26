(ns risingtide.test.feed.digest
  (:require
   [risingtide.feed.digest :refer :all]
   [risingtide
    [story :refer [with-score score] :as story]
    [feed :refer [min-timestamp max-timestamp] :as feed]
    [config :as config]
    [test :refer :all]]
   [midje.sweet :refer :all]))

(facts "about listing story set"
  (let [listing-story-set (->ListingStorySet nil)
        one-story-set (story/add listing-story-set jim-liked-ham)
        multi-action-digest (story/add one-story-set jim-sold-ham)
        multi-actor-digest (story/add one-story-set cutter-liked-ham)
        multi-actor-multi-action-digest (story/add one-story-set cutter-shared-ham)]

    (.stories one-story-set)
    => #{jim-liked-ham}

    multi-action-digest
    => (story/->MultiActionStory ham jim #{:listing_liked :listing_sold})
    (score multi-action-digest)
    => (score jim-sold-ham)
    
    multi-actor-digest
    => (story/->MultiActorStory ham :listing_liked #{jim cutter})
    (score multi-actor-digest)
    => (score cutter-liked-ham)
    
    multi-actor-multi-action-digest
    => (story/->MultiActorMultiActionStory ham {:listing_liked #{jim} :listing_shared #{cutter}})
    (score multi-actor-multi-action-digest)
    => (score cutter-shared-ham)))

(facts "about actor story set"
  (with-redefs [config/single-actor-digest-story-min 3]
    (let [actor-story-set (->ActorStorySet nil)
          one-story-set (story/add actor-story-set jim-liked-ham)
          actor-digest (reduce story/add one-story-set [jim-liked-bacon jim-liked-toast ])]
      (.stories one-story-set) => #{jim-liked-ham}
      actor-digest => (story/->MultiListingStory jim :listing_liked #{ham toast bacon}))))

(with-redefs [config/single-actor-digest-story-min 3]
  (tabular
   (fact "feed generation works"
     (seq (reduce feed/add (new-digest-feed) ?undigested)) => ?digested)
   ?undigested ?digested
   
   [jim-liked-ham]
   ;; digests to
   [jim-liked-ham]
   
   [jim-activated-ham jim-liked-ham jim-shared-ham jim-liked-bacon]
   ;; digests to
   [(story/->MultiActionStory ham jim #{:listing_liked :listing_activated :listing_shared}) jim-liked-bacon]
   
   ;; multiple users - one listing - one action
   [jim-liked-ham jim-liked-bacon cutter-liked-ham]
   ;; digests to
   [(story/->MultiActorStory ham :listing_liked #{jim cutter}) jim-liked-bacon]
   
   [jim-liked-bacon jim-liked-ham jim-liked-toast jim-shared-muffins]
   ;; digests to
   [(story/->MultiListingStory jim :listing_liked #{bacon ham toast}) jim-shared-muffins]))


(let [empty-feed (new-digest-feed)
      one-story-feed (feed/add empty-feed (with-score jim-liked-ham 10))
      two-story-feed (feed/add one-story-feed (with-score jim-liked-bacon 20))
      three-story-feed (feed/add two-story-feed (with-score jim-liked-toast 30))]
  (facts "about min/max timestamp tracking"
    (min-timestamp empty-feed) => nil
    (max-timestamp empty-feed) => nil

    (min-timestamp one-story-feed) => 10
    (max-timestamp one-story-feed) => 10
    
    (min-timestamp two-story-feed) => 10
    (max-timestamp two-story-feed) => 20

    (min-timestamp three-story-feed) => 10
    (max-timestamp three-story-feed) => 30))

(comment

(defn scored-seconds-ago
   [story seconds]
   (assoc story :score (- (now) seconds)))

 (def ssa scored-seconds-ago)

 (fact "expiration works"
   (let [index (index-predigested-feed [(ssa jim-shared-hams 90) (ssa jim-activated-hams 60) (ssa jon-shared-bacon 30)])]
     (map us (feed-from-index index)) => (map us [(story/multi-action-digest hams jim ["listing_shared" "listing_activated"]) jon-shared-bacon])
     (map us (feed-from-index (binding [*card-cache-ttl* 45] (expire-feed-indexes "magt:f:u:47:c" index)))) =>
     (map us [jon-shared-bacon]))))

