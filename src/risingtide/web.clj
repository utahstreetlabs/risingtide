(ns risingtide.web
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware.resource :only [wrap-resource]]
        [ring.middleware.file-info :only [wrap-file-info]]
        risingtide.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [risingtide.version :as version]
            [risingtide.config :as config]
            [risingtide.digest :as digest]
            [net.cgrand.enlive-html :as html]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(html/deftemplate layout "html/layout.html"
  [ctxt]
  [:body :.content] (html/content ctxt))

(html/defsnippet key-val-table "html/components.html" [:#key-val-table]
   [content]

   [:tbody :tr]
   (html/clone-for [[key value] content]
                   [:.key] (html/content (str key))
                   [:.value] (html/content (str value))))

(defn admin-info
  [processor]
  (sorted-map
   "version" version/version
   "environment" env
   "connections" (:connections processor)
   "cache size" (count @(:cache processor))
   "processor running" @(:run-processor processor)
   "flusher active count" (.getActiveCount (:flusher processor))
   "flusher completed count" (.getCompletedTaskCount (:flusher processor))
   "flusher terminating" (.isTerminating (:flusher processor))
   "flusher terminated" (.isTerminated (:flusher processor))
   "processor" processor
   ))

(defn migrate!
  [conn-spec type user-ids destination-shard]
  {:pre [(not (nil? type))
         (not (nil? user-ids))
         (not (nil? destination-shard))]}
  (dorun (digest/migrate-users! conn-spec type (.split user-ids ",") destination-shard))
  (str "migrated " type " feed for users " user-ids " to " destination-shard))

(defn handler
  [processor]
  (routes
   (GET "/" [] (layout (key-val-table (admin-info @processor))))
   (GET "/cache" [] (str @(:cache @processor)))
   (POST "/feeds/migrate" [type user-ids destination-shard]
         (migrate! (:connections @processor) type user-ids destination-shard))
   (POST "/cache/flusher/stop" []
         (.shutdownNow (:flusher @processor))
         "Stopped")
   (POST "/cache/flusher/start" []
         (if (.isTerminated (:flusher @processor))
           (do (swap! processor (fn [p] (assoc p :flusher (digest/cache-flusher (:cache p) (:connections p) (:cache-flush-frequency p)))))
               "Started")
           "Can not start flusher, old flusher not terminated"))))

(defn run!
  [processor-atom]
  (let [port (config/ports :admin)]
    (log/info (format "starting console on http://localhost:%s" port))
    (future (run-jetty
             (handler/site (handler processor-atom))
             {:port port}))))
