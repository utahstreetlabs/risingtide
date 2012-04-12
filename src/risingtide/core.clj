(ns risingtide.core
  (:require [accession.core :as redis]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn now [] (long (/ (.getTime (java.util.Date.)) 1000)))

(def env (keyword (or (System/getenv "RISINGTIDE_ENV") (System/getenv "RT_ENV") "development")))

(defn first-char
  [string-or-keyword]
  (first (name string-or-keyword)))

(defn safe-print-stack-trace
  [throwable]
  (try (let [w (java.io.PrintWriter. *out*)]
         (.printStackTrace throwable w)
         (.flush w))
    (catch Throwable t (log/error "failed to print stack trace with error" t))))

(defmacro bench
  [msg & forms]
  `(let [start# (.getTime (java.util.Date.))
         _# (log/debug "executing" ~msg)
         result# (do ~@forms)]
     (log/info ~msg "in" (- (.getTime (java.util.Date.)) start#) "ms")
     result#))
