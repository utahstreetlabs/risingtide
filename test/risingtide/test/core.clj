(ns risingtide.test.core
  (:use [risingtide core test])
  (:use [midje.sweet]))

(test-background)

(fact
 (first-char "hi") => \h
 (first-char :hi) => \h)
