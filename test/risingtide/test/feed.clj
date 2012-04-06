(ns risingtide.test.feed
  (:use [risingtide.feed])
  (:use [midje.sweet]))

(fact
  (feeds-by-type ["magt:f:u:1:c" "magt:f:u:1:n" "magt:f:u:2:c"]) =>
  {:card-feeds ["magt:f:u:2:c" "magt:f:u:1:c"] :network-feeds ["magt:f:u:1:n"]})

(fact
  (map-across-connections-and-feeds
   {:network-feeds {:hambo :clam} :card-feeds {:chumba :wumba}}
   ["magt:f:u:1:c" "magt:f:u:1:n" "magt:f:u:2:c"]
   (fn [c feeds] [feeds c])) =>
  [[["magt:f:u:1:n"] {:hambo :clam}] [["magt:f:u:2:c" "magt:f:u:1:c"] {:chumba :wumba}]])

