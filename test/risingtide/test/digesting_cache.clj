(ns risingtide.test.digesting-cache
  (:use risingtide.test
        [risingtide.digesting-cache]
        [risingtide.core :only [env]])
  (:use [midje.sweet]))

(against-background
  [(before :facts (reset-cache!))]

  (fact
    (against-background (env) => :test)

    (cached-stories (cache-story (listing-liked 1 3) 1234)) =>
    {"magt:c:l:3" #{(listing-liked 1 3 1234)},
     "magt:c:a:1" #{(listing-liked 1 3 1234)}})

  (fact
    (cache-story (listing-liked 2 12) 10)
    (cache-story (listing-liked 1 11) 30)

    @story-cache => {"magd:c:l:11" #{(listing-liked 1 11 30)},
                     "magd:c:a:1" #{(listing-liked 1 11 30)},
                     "magd:c:l:12" #{(listing-liked 2 12 10)},
                     "magd:c:a:2" #{(listing-liked 2 12 10)}
                     :low-score 0
                     :high-score 30}

    (expire-cached-stories story-cache 20)

    @story-cache => {"magd:c:l:11" #{(listing-liked 1 11 30)},
                     "magd:c:a:1" #{(listing-liked 1 11 30)}
                     :low-score 20
                     :high-score 30}
    )
  )