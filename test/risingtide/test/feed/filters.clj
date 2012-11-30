(ns risingtide.test.feed.filters
  (:require
   [risingtide.test]
   [midje.sweet :refer :all]
   [risingtide.feed.filters :refer :all]))

(facts "about for-user-feed?"
  (for-user-feed? {}) => truthy
  (for-user-feed? {:feed [:ev]}) => falsey
  (for-user-feed? {:feed nil}) => truthy
  (for-user-feed? {:feed [:ylf]}) => truthy
  (for-user-feed? {:feed ["ylf"]}) => truthy
  (for-user-feed? {:feed [:ylf :ev]}) => truthy
  (for-user-feed? {:feed ["ylf" "ev"]}) => truthy)

(facts "about the user feed actor blacklist"
  (binding [risingtide.config/*user-feed-actor-blacklist* {:test #{4}}]
    (for-user-feed? {}) => truthy
    (for-user-feed? {:actor-id 2 :feed [:ylf]}) => truthy
    (for-user-feed? {:actor-id 4 :feed [:ylf]}) => falsey
    (for-user-feed? {:feed [:ev]}) => falsey))

(facts "about for-everything-feed?"
  (for-everything-feed? {}) => truthy
  (for-everything-feed? {:feed nil}) => truthy
  (for-everything-feed? {:feed ["ylf"]}) => falsey
  (for-everything-feed? {:feed [:ylf]}) => falsey
  (for-everything-feed? {:feed ["ev"]}) => truthy
  (for-everything-feed? {:feed [:ev]}) => truthy
  (for-everything-feed? {:feed [:ylf :ev]}) => truthy
  (for-everything-feed? {:feed ["ylf" "ev"]}) => truthy)