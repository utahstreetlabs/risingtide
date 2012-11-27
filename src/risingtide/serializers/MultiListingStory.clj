(ns risingtide.serializers.MultiListingStory
  (:require [risingtide.serializers :refer [write-record read-record]]
            [risingtide.model.story :refer [map->MultiListingStory]])
  (:gen-class
   :extends com.esotericsoftware.kryo.Serializer))

(defn -write [this kryo output story]
  (write-record output story))

(defn -read [this kryo input type]
  (read-record input map->MultiListingStory))
