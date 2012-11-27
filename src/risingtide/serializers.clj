(ns risingtide.serializers
    (:require [carbonite
               [serializer :refer [clj-print clj-read]]]))


(defn write-record [output record]
  (clj-print output (into {} record))
  (clj-print output (meta record)))

(defn read-record [input constructor]
  (-> (constructor (clj-read input))
      (with-meta (clj-read input))))

