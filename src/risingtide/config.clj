(ns risingtide.config
  (:require [risingtide.core :as core]))

(def env (keyword (or (System/getenv "RISINGTIDE_ENV") (System/getenv "RT_ENV") "development")))

(def redis
  {:development {:resque {}
                 :everything-card-feed {}
                 :card-feeds-1 {}
                 :card-feeds-2 {:db 1}
                 :shard-config {}}
   :test {:resque {}
          :everything-card-feed {} :card-feeds-1 {}
          :shard-config {}}
   :staging {:resque {:host "staging3.copious.com"}
             :everything-card-feed {:host "staging4.copious.com"}
             :card-feeds-1 {:host "staging4.copious.com"}
             :card-feeds-2 {:host "staging4.copious.com" :db 1}
             :shard-config {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :everything-card-feed {:host "demo1.copious.com"}
          :card-feeds-1 {:host "demo1.copious.com"}
          :card-feeds-2 {:host "demo1.copious.com" :db 1}
          :shard-config {:host "demo1.copious.com"}}
   :production {:resque {:host "resque-redis-master.copious.com"}
                :everything-card-feed {:host "rt-card-feeds-redis.copious.com"}
                :card-feeds-1 {:host "rt-card-feeds-1-redis.copious.com"}
                :card-feeds-2 {:host "rt-card-feeds-2-redis.copious.com"}
                :shard-config {:host "rt-shard-config-redis.copious.com"}}})

(defn redis-config [] (redis env))

(def mysql-creds
  {:user "utah"
   :password "Utah5tr33t"})

(defn db [& {:as params}]
  (merge mysql-creds params))

(def brooklyn-db
  {:development (db :db "utah_development")
   :test (db :db "utah_test")
   :staging (db :db "utah_staging" :host "staging.copious.com")
   :demo (db :db "utah_demo" :host "demo1.copious.com")
   :production (db :db "utah_production"
                   :user "utah_ro"
                   :host "db1.copious.com")})

(def pyramid-db
  {:development (db :db "pyramid_development")
   :test (db :db "pyramid_test")
   :staging (db :db "pyramid_staging" :host "staging.copious.com")
   :demo (db :db "pyramid_demo" :host "demo1.copious.com")
   :production (db :db "pyramid_production"
                   :user "utah_ro"
                   :host "db3.copious.com")})

(defn brooklyn [] (brooklyn-db env))
(defn pyramid [] (pyramid-db env))

(def max-card-feed-size 500)
(def initial-feed-size 1000)
(def default-card-shard "1")
