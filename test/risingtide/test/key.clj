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

  (actor-card-story 47) => "magt:c:a:47"
  (listing-card-story 100) => "magt:c:l:100"
  (tag-card-story 2) => "magt:c:t:2"

  (actor-network-story 47) => "magt:n:a:47"
  (listing-network-story 100) => "magt:n:l:100"
  (tag-network-story 2) => "magt:n:t:2"

  (user-feed 47 "a") => "magt:f:u:47:a"
  (user-feed 47 "l") => "magt:f:u:47:l"
  (user-feed 47 "t") => "magt:f:u:47:t"

  (everything-feed) => "magt:f:c"

  (against-background
    (env) => "test"))
