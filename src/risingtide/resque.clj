(ns risingtide.resque
  (:require
   [risingtide
    [redis :as redis]]
   [clojure.data.json :as json]))

(defn args-from-resque [resque-job action-name]
  (when (= action-name (:class resque-job))
    (:args resque-job)))

(defn pop-from-resque [resque-pool queue]
  (when-let [string (let [r (.getResource resque-pool)]
                         (try
                           (.lpop r queue)
                           (finally (.returnResource resque-pool r))))]
    (json/read-json string)))
