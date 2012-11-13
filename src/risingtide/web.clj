(ns risingtide.web
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware.resource :only [wrap-resource]]
        [ring.middleware.file-info :only [wrap-file-info]]
        risingtide.core
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [risingtide
             [config :as config]
             [version :as version]
             [active-users :refer [active-users]]
             [redis :as redis]]
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
  []
  (sorted-map
   "version" version/version
   "environment" config/env
   "active users" (count (active-users (redis/redii)))))

(defn handler
  []
  (routes
   (GET "/" [] (layout (key-val-table (admin-info))))))

(defn run!
  []
  (let [port config/admin-port]
    (log/info (format "starting console on http://localhost:%s" port))
    (run-jetty
     (handler/site (handler))
     {:port port})))
