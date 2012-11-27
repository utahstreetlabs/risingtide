(ns risingtide.test.support.entities)

;; users
(defmacro defuser
  [n id]
  `(do
    (def ~n ~id)))

(defuser jim 1)
(defuser jon 2)
(defuser bcm 3)
(defuser dave 4)
(defuser rob 5)
(defuser cutter 6)
(defuser kaitlyn 7)
(defuser courtney 8)


;; profiles

(def mark-z :markz)

;; listings

(def bacon 100)
(def ham 101)
(def eggs 102)
(def muffins 103)
(def breakfast-tacos 104)
(def toast 105)
(def scones 106)
(def croissants 107)
(def danishes 108)
(def omelettes 109)
(def nail-polish 110)

;; tags

(def breakfast 200)
(def lunch 201)
(def dinner 202)
(def dangerous 203)
