(ns risingtide.test.feed.set
  (:require risingtide.test [midje.sweet :refer :all])
  (:require
   [copious.domain.testing.entities :refer :all]
   [risingtide.test.support.fixtures :refer :all]
   [risingtide
    [active-users :refer [active-users]]]
   [risingtide.feed
    [persist :refer [initialize-digest-feed delete-feeds!]]]
   [risingtide.feed.set :refer :all]
   [risingtide.model.feed.digest :refer [new-digest-feed]]))

(facts "add!"
  (fact "adds the story to the feed "
    (let [feed-set (atom {jim (atom (new-digest-feed cutter-liked-toast))})]
      (add! {} feed-set jim jim-liked-ham)

      (seq @(@feed-set jim))
      => [jim-liked-ham cutter-liked-toast]))

  (fact "loads the feed from storage if it isn't in this set yet, and then adds the story"
    (prerequisite (initialize-digest-feed anything anything jim-liked-ham)
                  => (new-digest-feed jim-liked-ham))
    (let [feed-set (atom {})]
      (add! {} feed-set jim jim-liked-ham)

      (seq @(@feed-set jim))
      => [jim-liked-ham])))

(facts "remove!"
  (fact "removes stories about a listing from a user's feed"
    (let [feed-set (atom {jim (atom (new-digest-feed cutter-liked-toast cutter-liked-omelettes))})]
      (remove! {} feed-set jim toast)

      (seq @(@feed-set jim))
      => [cutter-liked-omelettes]))

  (fact "loads the feed from storage if it isn't in this set yet, and then removes the stories"
    (prerequisite (initialize-digest-feed anything anything)
                  => (new-digest-feed cutter-liked-toast cutter-liked-omelettes))
    (let [feed-set (atom {})]
      (remove! {} feed-set jim toast)

      (seq @(@feed-set jim))
      => [cutter-liked-omelettes])))

(facts "expire-inactive!"
  (fact "it filters out and deletes inactive feeds"
    (prerequisite (delete-feeds! anything #{jim cutter}) => nil
                  (active-users {}) => [rob])
    (let [feed-set (atom {jim (atom (new-digest-feed cutter-liked-toast))
                          cutter (atom (new-digest-feed rob-liked-toast))
                          rob (atom (new-digest-feed jim-liked-toast))})]
      (expire-inactive! {} feed-set)

      (@feed-set jim)
      => nil

      (@feed-set cutter)
      => nil

      (seq @(@feed-set rob))
      => [jim-liked-toast])))