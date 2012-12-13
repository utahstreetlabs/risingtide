(ns risingtide.storm.remove-bolts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [risingtide
             [core :refer [now]]]
            [backtype.storm [clojure :refer [emit-bolt! defbolt ack! bolt]]]
            [metrics.meters :refer [defmeter mark!]]))

(defbolt prepare-removals ["message" "user-id" "listing-id"]
  [{{user-id :user_id listing-id :listing_id} "removal" :as tuple} collector]
  (emit-bolt! collector [:remove user-id listing-id] :anchor tuple)
  (ack! collector tuple))


