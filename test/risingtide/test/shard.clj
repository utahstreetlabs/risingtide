(ns risingtide.test.shard
  (:require [risingtide
             [test :refer :all]]
            [risingtide.feed.persist.shard.config :as shard-config]
            [risingtide.feed.persist.shard :refer :all]
            [midje.sweet :refer :all]))

(expose risingtide.feed.persist.shard/feeds-by-shard
        risingtide.feed.persist.shard/flatten-keys)

(fact
  (flatten-keys {:a 1 :b {:c 2 :d 3}} "-") => {:a 1 :b-c 2 :b-d 3})

(fact
  (feeds-by-shard nil  ["magt:f:u:1:c" "magt:f:u:2:c"]) =>
  {:card-feeds-1 ["magt:f:u:2:c" "magt:f:u:1:c"]}
  (provided
    (shard-config/card-feed-shard-key nil "1") => "1"
    (shard-config/card-feed-shard-key nil "2") => "1"))

(fact
  (feeds-by-shard nil  ["magt:f:u:1:c" "magt:f:u:2:c"]) =>
  {:card-feeds-2 ["magt:f:u:2:c"] :card-feeds-1 ["magt:f:u:1:c"]}
  (provided
    (shard-config/card-feed-shard-key nil "1") => "1"
    (shard-config/card-feed-shard-key nil "2") => "2"))

(let [conn-spec {:card-feeds-1 {:chumba :wumba}}]
 (fact
   (map-across-connections-and-feeds
    conn-spec
    ["magt:f:u:1:c" "magt:f:u:2:c"]
    (fn [c feeds] [feeds c])) =>
    [[["magt:f:u:2:c" "magt:f:u:1:c"] {:chumba :wumba}]]
    (provided
      (shard-config/card-feed-shard-keys conn-spec "1") => ["1"]
      (shard-config/card-feed-shard-keys conn-spec "2") => ["1"])))

