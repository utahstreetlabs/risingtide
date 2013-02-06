(ns risingtide.test.storm.interests-bolts
  (:require risingtide.test
            [risingtide.storm.interests-bolts :refer :all]
            [midje.sweet :refer :all]))

(fact "emit-scores! throws an exception if it isn't given a score for each user id "
  (emit-scores! nil {"user-ids" [1 2 3]} {1 1 2 0} :test)
  => (throws AssertionError))
