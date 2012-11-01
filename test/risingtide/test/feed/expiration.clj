(ns risingtide.test.feed.expiration
  (:require
   [risingtide.test]
   [risingtide.test.support.stories :as stories]
   [midje.sweet :refer :all]

   [risingtide
    [core :refer [now]]
    [config :as config]]
   [risingtide.feed.expiration :refer :all]
   [risingtide.model [timestamps :refer [with-timestamp]]]
   [risingtide.model.feed [digest :refer [new-digest-feed]]]))

(def now-in-seconds 100)
(def feed (new-digest-feed (with-timestamp (stories/listing-liked 1 2 nil nil) 1)
                           (with-timestamp (stories/listing-liked 3 4 nil nil) 50)
                           (with-timestamp (stories/listing-liked 5 6 nil nil) 90)
                           ;; in the future, somehow
                           (with-timestamp (stories/listing-liked 7 8 nil nil) 110)))

(with-redefs [config/*digest-cache-ttl* 50
              now (constantly now-in-seconds)]
  (fact
    (expire feed) => [(stories/listing-liked 7 8 nil nil) (stories/listing-liked 5 6 nil nil)]))

