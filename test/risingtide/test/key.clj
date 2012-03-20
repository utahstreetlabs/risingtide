(ns risingtide.test.key
  (:use [risingtide.key]
        [risingtide.core :only [env]]
        [midje.sweet]))

(facts
  (env-prefix) => "magt"
  (format-key "1" "2") => "magt:1:2"

  (interest 24 "a") => "magt:i:u:24:a"
  (interest 24 "t") => "magt:i:u:24:t"
  (interest 24 "l") => "magt:i:u:24:l"

  (card-actor-story 47) => "magt:c:a:47"
  (card-listing-story 100) => "magt:c:l:100"
  (card-tag-story 2) => "magt:c:t:2"

  (network-actor-story 47) => "magt:n:a:47"
  (network-listing-story 100) => "magt:n:l:100"
  (network-tag-story 2) => "magt:n:t:2"

  (user-feed 47 "c") => "magt:f:u:47:c"
  (user-feed 47 "n") => "magt:f:u:47:n"

  (everything-feed) => "magt:f:c"

  (feed-type-user-id-from-key "magt:f:u:47:c") => [:card "47"]
  (feed-type-user-id-from-key "magt:f:u:47:n") => [:network "47"]

  (against-background
    (env) => :test))