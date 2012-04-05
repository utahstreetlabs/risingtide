(ns risingtide.web
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware.resource :only [wrap-resource]]
        [ring.middleware.file-info :only [wrap-file-info]]
        risingtide.core)
  (:require [clojure.tools.logging :as log]
            [risingtide.digesting-cache :as dc]
            [risingtide.version :as version]
            [risingtide.config :as config]
            [net.cgrand.enlive-html :as html]))

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
   "cache size" (count (dc/all-stories @(:cache processor)))
   "cache expiration running" @(:run-expiration-thread processor)
   "processor running" @(:run-processor processor)))

(defn handler
  [processor-atom]
  (fn [request]
    (let [processor (deref processor-atom)]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (layout
              (if processor
                (if (= "/cache" (:uri request))
                  (str @(:cache processor))
                  (key-val-table (admin-info processor)))
                "server not yet started"))})))

(defn run!
  [processor-atom]
  (let [port (config/ports :admin)]
    (log/info (format "starting console on http://localhost:%s" port))
    (future (run-jetty
             (-> (handler processor-atom)
                 (wrap-resource "public")
                 (wrap-file-info))
             {:port port}))))
