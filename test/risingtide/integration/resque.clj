(ns risingtide.integration.core
  (:use risingtide.core
        risingtide.integration.support
        risingtide.test
        risingtide.resque)
  (:use [midje.sweet])
  (:require [risingtide.stories :as story]
            [risingtide.redis :as redis]))

(fact
  (redis/with-jedis* (:resque (redis/redii :test))
    (fn [jedis]
      (.del jedis (into-array String ["high" "low"]))
      (.rpush jedis "high" "1")
      (.rpush jedis "low" "2")
      (.rpush jedis "high" "3")
      (.rpush jedis "low" "4")))
  (take 4 (jobs (atom true) (:resque (redis/redii :test)) ["high" "low"])) =>
  ["1" "3" "2" "4"])
