(ns risingtide.test.v2.feed-set.filters
  (require
   [midje.sweet :refer :all]
   [risingtide.v2.feed-set :refer :all]))

(facts
  (user-feed-story? {}) => "ylf"
  (user-feed-story? {:feed "ev"}) => nil
  (user-feed-story? {:feed ["ev"]}) => nil
  (user-feed-story? {:feed "ylf"}) => "ylf"
  (user-feed-story? {:feed ["ylf"]}) => "ylf"
  (user-feed-story? {:feed ["ylf" "ev"]}) => "ylf"

  (everything-feed-story? {}) => "ev"
  (everything-feed-story? {:feed "ylf"}) => nil
  (everything-feed-story? {:feed ["ylf"]}) => nil
  (everything-feed-story? {:feed "ev"}) => "ev"
  (everything-feed-story? {:feed ["ev"]}) => "ev"
  (everything-feed-story? {:feed ["ylf" "ev"]}) => "ev")