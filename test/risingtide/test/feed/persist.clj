(ns risingtide.test.feed.persist
  (:require [risingtide
             [config :refer [env]]]
            [risingtide.feed [persist :refer :all]]

            risingtide.test
            [risingtide.test.support.stories :refer :all]
            [midje.sweet :refer :all]))

(defmacro facts:dont-change-during-serialization [& stories]
  (concat
   '(do)
   (for [story stories]
     `(fact (str story "doesn't change during serialization")
        (decode (encode ~story))
        => ~story))))

(facts:dont-change-during-serialization
 (tag-liked 1 2)
 (listing-liked 1 2 6 [3 4] [:ev])
 (listing-commented 1 2 6 [3 4] "foo" [:ev])
 (listing-activated 1 2 6 [3 4] [:ev])
 (listing-sold 1 2 6 [3 4] 1 [:ev])
 (listing-shared 1 2 6 [3 4] :facebook [:ev])

 (multi-listing-story 1 :listing_liked #{2} 1)
 (multi-actor-story 1 :listing_liked #{2 3})
 (multi-action-story 1 2 #{:listing_liked :listing_shared})
 (multi-actor-multi-action-story 1 {:listing_liked #{1} :listing_shared #{3 4}}))
