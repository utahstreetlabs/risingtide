(ns risingtide.core
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn now [] (long (/ (.getTime (java.util.Date.)) 1000)))

(defn safe-print-stack-trace
  [throwable]
  (try (let [w (java.io.PrintWriter. *out*)]
         (.printStackTrace throwable w)
         (.flush w))
    (catch Throwable t (log/error "failed to print stack trace with error" t))))

(defmacro bench-if-longer-than
  [threshold msg & forms]
  `(let [start# (.getTime (java.util.Date.))
         _# (log/debug "executing" ~msg)
         result# (do ~@forms)
         duration# (- (.getTime (java.util.Date.)) start#)]
     (when (> duration# ~threshold)
       (log/info ~msg "in" duration# "ms"))
     result#))

(defmacro bench
  [msg & forms]
  `(bench-if-longer-than -1 ~msg ~@forms))

(defn pmap-in-batches
  ([f coll n]
     (pmap #(doall (map f %)) (partition-all n coll)))
  ([f coll]
     (pmap-in-batches f coll 1000)))

(defn log-err [message e ns]
  (.printStackTrace e (log/log-stream :error ns))
  (log/error message e))