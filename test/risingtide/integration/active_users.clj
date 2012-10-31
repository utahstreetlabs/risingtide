(ns risingtide.integration.active-users
  (:require
   [risingtide.redis :as redis]
   [risingtide.active-users :refer :all]

   risingtide.test
   [risingtide.integration.support :refer [clear-redis!]]
   [midje.sweet :refer :all]))

(facts "should store active users"
  ;; add active users with a 1 second expiry
  (add-active-users (redis/redii) 1 1 2)

  (active-users (redis/redii)) =>
  (contains #{1 2})

  ;; wait for the expiry
  (Thread/sleep 2000)

  (active-users (redis/redii)) =>
  [])


