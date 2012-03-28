(ns risingtide.test.digesting-cache
  (:use risingtide.test
        [risingtide.digesting-cache]
        [risingtide.core :only [env now]])
  (:use [midje.sweet]))

(against-background
  [(before :facts (reset-cache!))]

  (fact
    (cached-stories (cache-story (listing-liked 1 3) 1234)) =>
    {"magt:c:l:3" #{(listing-liked 1 3 1234)},
     "magt:c:a:1" #{(listing-liked 1 3 1234)}})

  (fact
    (let [now (now)
          low (+ now 100)
          med (+ now 200)
          high (+ now 300)]
     (cache-story (listing-liked 2 12) low)
     (cache-story (listing-liked 1 11) high)

     @story-cache => {"magt:c:l:11" #{(listing-liked 1 11 high)},
                      "magt:c:a:1" #{(listing-liked 1 11 high)},
                      "magt:c:l:12" #{(listing-liked 2 12 low)},
                      "magt:c:a:2" #{(listing-liked 2 12 low)}
                      :low-score now
                      :high-score high}

     (expire-cached-stories story-cache med)

     @story-cache => {"magt:c:l:11" #{(listing-liked 1 11 high)},
                      "magt:c:a:1" #{(listing-liked 1 11 high)}
                      :low-score med
                      :high-score high})))