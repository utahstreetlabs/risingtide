(ns risingtide.config
  (:require [risingtide.core :as core]))

(def redis
  {:development {:resque {}
                 :everything-card-feed {}
                 :card-feeds-1 {}
                 :card-feeds-2 {:db 1}
                 :network-feeds {} :watchers {} :stories {}
                 :shard-config {}}
   :test {:resque {}
          :everything-card-feed {} :card-feeds-1 {}
          :network-feeds {} :interests {} :watchers {} :stories {}
          :shard-config {}}
   :staging {:resque {:host "staging3.copious.com"}
             :everything-card-feed {:host "staging4.copious.com"}
             :card-feeds-1 {:host "staging4.copious.com"}
             :card-feeds-2 {:host "staging4.copious.com" :db 1}
             :network-feeds {:host "staging4.copious.com"}
             :watchers {:host "staging4.copious.com"}
             :stories {:host "staging4.copious.com"}
             :shard-config {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :everything-card-feed {:host "demo1.copious.com"}
          :card-feeds-1 {:host "demo1.copious.com"}
          :card-feeds-2 {:host "demo1.copious.com" :db 1}
          :network-feeds {:host "demo1.copious.com"}
          :watchers {:host "demo1.copious.com"}
          :stories {:host "demo1.copious.com"}
          :shard-config {:host "demo1.copious.com"}
          }
   :production {:resque {:host "resque-redis-master.copious.com"}
                :everything-card-feed {:host "rt-card-feeds-redis.copious.com"}
                :card-feeds-1 {:host "rt-card-feeds-1-redis.copious.com"}
                :card-feeds-2 {:host "rt-card-feeds-2-redis.copious.com"}
                :network-feeds {:host "rt-network-feeds-redis.copious.com"}
                :watchers {:host "rt-watchers-redis.copious.com"}
                :stories {:host "rt-stories-redis.copious.com"}
                :shard-config {:host "rt-shard-config-redis.copious.com"}}})

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
                   :host "db3.copous.com")})

(defn brooklyn [] (brooklyn-db  core/env))
(defn pyramid [] (pyramid-db  core/env))

(def digest
  {:development true
   :staging true
   :production true})

(def max-card-feed-size 500)
(def max-network-feed-size 250)
(def max-story-bucket-size 1000)
(def max-story-union 100)
(def initial-feed-size 1000)
(def single-actor-digest-story-min 15)
(def default-card-shard "1")

(def ports {:admin 4050
            :mycroft 4055})
