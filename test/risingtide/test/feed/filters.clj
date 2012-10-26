(ns risingtide.test.feed.filters
  (:require
   [midje.sweet :refer :all]
   [risingtide.feed.filters :refer :all]))

(facts
  (for-user-feed? {}) => :ylf
  (for-user-feed? {:feed [:ev]}) => nil
  (for-user-feed? {:feed nil}) => nil
  (for-user-feed? {:feed [:ylf]}) => :ylf
  (for-user-feed? {:feed ["ylf"]}) => :ylf
  (for-user-feed? {:feed [:ylf :ev]}) => :ylf
  (for-user-feed? {:feed ["ylf" "ev"]}) => :ylf

  (for-everything-feed? {}) => :ev
  (for-everything-feed? {:feed ["ylf"]}) => nil
  (for-everything-feed? {:feed [:ylf]}) => nil
  (for-everything-feed? {:feed ["ev"]}) => :ev
  (for-everything-feed? {:feed [:ev]}) => :ev
  (for-everything-feed? {:feed [:ylf :ev]}) => :ev
  (for-everything-feed? {:feed ["ylf" "ev"]}) => :ev)