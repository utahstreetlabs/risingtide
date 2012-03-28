(ns risingtide.integration.core
  (:use risingtide.core
        risingtide.integration.support
        risingtide.test
        risingtide.resque)
  (:use [midje.sweet])
  (:require [risingtide.stories :as story]
            [accession.core :as redis]))


(fact
  (redis/with-connection (redis/connection-map)
    (redis/del "high" "low")
    (redis/rpush "high" 1)
    (redis/rpush "low" 2)
    (redis/rpush "high" 3)
    (redis/rpush "low" 4))
  (take 4 (jobs (atom true) (redis/connection-map) ["high" "low"])) =>
  ["1" "3" "2" "4"])
