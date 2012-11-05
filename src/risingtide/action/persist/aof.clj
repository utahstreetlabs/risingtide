(ns risingtide.action.persist.aof
  (:require
   [clojure
    [set :refer [map-invert rename-keys]]
    [string :as str]
    [walk :refer [keywordize-keys]]]
   [risingtide
    [config :as config]
    [persist :refer [keywordize convert-to-kw-set convert-to-set]]])
  (:import org.productivity.java.syslog4j.Syslog))

(defn syslog []
  (doto (Syslog/getInstance "unix_syslog")))

(defn write! [syslog string]
  (.info syslog string))
