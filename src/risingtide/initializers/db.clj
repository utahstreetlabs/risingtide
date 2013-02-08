(ns risingtide.initializers.db
  (:require [copious.domain.util.db :refer [init-dbs assign-entities]]
            [risingtide.config :as config]))

(init-dbs
 pyramid mysql (config/pyramid)
 brooklyn mysql (config/brooklyn))

(assign-entities
 copious.domain.like/likes pyramid)

