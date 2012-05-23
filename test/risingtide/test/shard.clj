(ns risingtide.test.shard
  (:use [risingtide
         test shard])
  (:use [midje.sweet])
  (:require [risingtide.shard.config :as shard-config]))

(expose risingtide.shard/feeds-by-type
        risingtide.shard/flatten-keys)

(fact
  (flatten-keys {:a 1 :b {:c 2 :d 3}} "-") => {:a 1 :b-c 2 :b-d 3})

(fact
  (feeds-by-shard nil  ["magt:f:u:1:c" "magt:f:u:1:n" "magt:f:u:2:c"]) =>
  {:card-feeds-1 ["magt:f:u:2:c" "magt:f:u:1:c"] :network-feeds ["magt:f:u:1:n"]}
  (provided
    (shard-config/card-feed-shard-key nil "1") => "1"
    (shard-config/card-feed-shard-key nil "2") => "1"))

(fact
  (feeds-by-shard nil  ["magt:f:u:1:c" "magt:f:u:1:n" "magt:f:u:2:c"]) =>
  {:card-feeds-2 ["magt:f:u:2:c"] :card-feeds-1 ["magt:f:u:1:c"] :network-feeds ["magt:f:u:1:n"]}
  (provided
    (shard-config/card-feed-shard-key nil "1") => "1"
    (shard-config/card-feed-shard-key nil "2") => "2"))

(fact
  (map-across-connections-and-feeds
   {:network-feeds {:hambo :clam} :card-feeds-1 {:chumba :wumba}}
   ["magt:f:u:1:c" "magt:f:u:1:n" "magt:f:u:2:c"]
   (fn [c feeds] [feeds c])) =>
   [[["magt:f:u:1:n"] {:hambo :clam}] [["magt:f:u:2:c" "magt:f:u:1:c"] {:chumba :wumba}]]
   (provided
    (shard-config/card-feed-shard-key nil "1") => "1"
    (shard-config/card-feed-shard-key nil "2") => "1"))

