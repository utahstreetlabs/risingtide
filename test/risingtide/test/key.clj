(ns risingtide.test.key
  (:require [risingtide
             [key :refer :all]
             [test :refer :all]]
            [risingtide.config :refer [env]]
            [midje.sweet :refer :all]))

(facts
  (format-key "1" "2") => "magt:1:2"

  (user-feed 47) => "magt:f:u:47:c"

  (everything-feed) => "magt:f:c"

  (type-user-id-from-feed-key "magt:f:u:47:c") => [:card "47"])
