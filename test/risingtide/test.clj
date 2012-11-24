(ns risingtide.test
  (:require
   [risingtide.core :refer :all]
   [risingtide.config]
   [risingtide.model
    [story :as story]
    [timestamps :refer [with-timestamp timestamp]]]
   [midje.sweet :refer :all]))

(alter-var-root #'risingtide.config/env (constantly :test))

(defmacro expose
  "def a variable in the current namespace. This can be used to expose a private function."
  [& vars]
  `(do
     ~@(for [var vars]
         `(def ~(symbol (name var)) (var ~var)))))



