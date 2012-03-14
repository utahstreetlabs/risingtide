(ns risingtide.test.core
  (:use [risingtide.core])
  (:use [midje.sweet]))

(fact
 (first-char "hi") => \h
 (first-char :hi) => \h)
