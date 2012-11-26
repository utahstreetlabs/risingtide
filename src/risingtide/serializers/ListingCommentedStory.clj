(ns risingtide.serializers.ListingCommentedStory
  (:require [risingtide.serializers :refer [write-record read-record]]
            [risingtide.model.story :refer [map->ListingCommentedStory]])
  (:gen-class
   :extends com.esotericsoftware.kryo.Serializer))

(defn -write [this kryo output story]
  (write-record output story))

(defn -read [this kryo input type]
  (read-record input map->ListingCommentedStory))
