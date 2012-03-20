(ns risingtide.test.digesting-cache
  (:use [risingtide.digesting-cache]
        [risingtide.core :only [env]])
  (:use [midje.sweet]))

(fact
  (against-background
   (before :facts (reset-cache!))
   (env) => :test)
  (cache-story {:actor_id 1 :listing_id 3 :type "listing_liked"} 1234) =>
  {"magt:c:l:3" #{{:actor_id 1, :listing_id 3, :type "listing_liked" :score 1234}},
   "magt:c:a:1" #{{:actor_id 1, :listing_id 3, :type "listing_liked" :score 1234}}})
