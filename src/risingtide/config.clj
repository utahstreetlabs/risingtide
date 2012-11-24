(ns risingtide.config
  (:require [risingtide.core :as core]))

(def env (keyword (or (System/getProperty "risingtide.env") (System/getenv "RISINGTIDE_ENV") (System/getenv "RT_ENV") "development")))

(def redis
  {:development {:resque {}
                 :everything-card-feed {}
                 :card-feeds-1 {}
                 :card-feeds-2 {:db 1}
                 :shard-config {}
                 :active-users {}}
   :test {:resque {}
          :everything-card-feed {} :card-feeds-1 {}
          :active-users {}
          :shard-config {}}
   :staging {:resque {:host "staging3.copious.com"}
             :everything-card-feed {:host "staging4.copious.com"}
             :card-feeds-1 {:host "staging4.copious.com" :db 2}
             :card-feeds-2 {:host "staging4.copious.com" :db 3}
             :active-users {:host "staging4.copious.com"}
             :shard-config {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :everything-card-feed {:host "demo1.copious.com"}
          :card-feeds-1 {:host "demo1.copious.com"}
          :card-feeds-2 {:host "demo1.copious.com" :db 1}
          :active-users {:host "demo1.copious.com"}
          :shard-config {:host "demo1.copious.com"}}
   :production {:resque {:host "resque-redis-master.copious.com"}
                :everything-card-feed {:host "rt-feeds-1-redis.copious.com"}
                :card-feeds-1 {:host "rt-feeds-1-redis.copious.com"}
                :card-feeds-2 {:host "rt-feeds-2-redis.copious.com"}
                :active-users {:host "rt-active-users-redis.copious.com"}
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

(def action-solr-config
  {:development "http://127.0.0.1:8950/solr"
   :test "http://127.0.0.1:8951/solr"
   :staging "http://127.0.0.1:8983/solr"
   :demo "http://demo1.copious.com:8983/solr"
   :production "http://solr-rt1.copious.com:8983/solr"})
(defn action-solr []
  (action-solr-config env))

(def max-card-feed-size 500)
(def initial-feed-size 1000)
(def default-card-shard "1")
(def ^:dynamic *digest-cache-ttl* (* 6 60 60))
;; number of seconds to wait between expiring stories in feed sets
(def feed-expiration-delay 60)

;;; storm topology config ;;;

(def active-user-bolt-batch-size 500)
(def recent-actions-max-follows 100)
(def recent-actions-max-likes 100)
(def recent-actions-max-seller-listings 100)
(def drpc-max-stories 60)
(def recent-actions-max-recent-stories 2000)


(def local-drpc-port-config
  ;; this only gets used in dev and test
  {:development 4050
   :test 4051})

(defn local-drpc-port []
  (local-drpc-port-config env))

(def admin-port 4055)