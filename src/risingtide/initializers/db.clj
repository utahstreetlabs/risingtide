(ns risingtide.initializers.db
  (:require [copious.domain.util.db :refer [defdb]]
            [risingtide.config :as config]))


(defdb pyramid :mysql (config/pyramid)
  copious.domain.like/likes)

(defdb brooklyn :mysql (config/brooklyn))
