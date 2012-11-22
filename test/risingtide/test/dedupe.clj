(ns risingtide.test.dedupe
  (:require [risingtide
             [dedupe :refer :all]
             test]
            [midje.sweet :refer :all]))

(facts "about dedupe"
  (reset-cache!)

  (= {:foo :bar} {:foo :bar})
  => true

  (identical? {:foo :bar} {:foo :bar})
  => false

  (identical? (dedupe {:foo :bar}) (dedupe {:foo :bar}))
  => true

  ;; test expiry
  (reset-cache!)
  (def old-obj (dedupe {:foo :bar} :for 100))
  (expire-cache!)

  (identical? (dedupe {:foo :bar}) old-obj)
  => true

  (Thread/sleep 100)
  (expire-cache!)

  (identical? (dedupe {:foo :bar}) old-obj)
  => false

  )
