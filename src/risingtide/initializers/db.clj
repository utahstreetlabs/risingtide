(ns risingtide.initializers.db
  (:require [korma [db :refer :all] [core :refer [database]] [config :refer [set-delimiters]]]
            [copious.domain.like :as like]
            [risingtide.config :as config]))

;; unmap dbs so they reload properly
(ns-unmap *ns* 'pyramid)

(defn alter-db [entity-var db]
  (alter-var-root entity-var #(database % db)))

(defdb pyramid
  (mysql (merge (config/pyramid) {:delimiters "`"})))

(alter-db #'like/likes pyramid)

;; primary db _must_ be defined last!

(defdb brooklyn
  (mysql (merge (config/brooklyn) {:delimiters "`"})))

