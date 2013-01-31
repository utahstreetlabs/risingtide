(ns risingtide.config
  (:require [risingtide.core :as core]))

(defonce env (keyword (or (System/getProperty "risingtide.env") (System/getenv "RISINGTIDE_ENV") (System/getenv "RT_ENV") "development")))

(def redis
  {:development {:resque {}
                 :everything-card-feed {}
                 :card-feeds-1 {}
                 :card-feeds-2 {:db 1}
                 :stories {}
                 :shard-config {}
                 :active-users {}}
   :test {:resque {}
          :everything-card-feed {} :card-feeds-1 {}
          :stories {}
          :active-users {} :shard-config {}}
   :staging {:resque {:host "staging3.copious.com"}
             :everything-card-feed {:host "staging4.copious.com"}
             :card-feeds-1 {:host "staging4.copious.com" :db 2}
             :card-feeds-2 {:host "staging4.copious.com" :db 3}
             :active-users {:host "staging4.copious.com"}
             :shard-config {:host "staging4.copious.com"}
             :stories {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :everything-card-feed {:host "demo1.copious.com"}
          :card-feeds-1 {:host "demo1.copious.com"}
          :card-feeds-2 {:host "demo1.copious.com" :db 1}
          :stories {:host "demo1.copious.com"}
          :active-users {:host "demo1.copious.com"}
          :shard-config {:host "demo1.copious.com"}}
   :production {:resque {:host "resque-redis-master.copious.com"}
                :everything-card-feed {:host "rt-feeds-1-redis.copious.com"}
                :card-feeds-1 {:host "rt-feeds-1-redis.copious.com"}
                :card-feeds-2 {:host "rt-feeds-2-redis.copious.com"}
                :stories {:host "rt-stories-redis.copious.com"}
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
(def encoding-cache-ttl (* 6 60 60 1000))
;; number of seconds to wait between expiring stories in feed sets
(def feed-expiration-delay 120)

(def ^:dynamic *user-feed-actor-blacklist*
  {:development #{}
   :test #{}
   :staging #{}
   :demo #{}
   :production #{38319}})

(defn actor-blacklisted-from-user-feed? [id]
  ((*user-feed-actor-blacklist* env) id))


;;; storm topology config ;;;

(def active-user-bolt-batch-size 500)
(def recent-actions-max-follows 200)
(def recent-actions-max-likes 200)
(def recent-actions-max-seller-listings 200)
(def drpc-max-stories 60)
(def recent-actions-max-recent-stories 2000)
;; linda and ajm - blacklist them because they list too much and break
;; drpc builds
(def drpc-blacklist #{38319 11089})
(def minimum-drpc-actions 300)


(def local-drpc-port-config
  ;; this only gets used in dev and test
  {:development 4050
   :test 4051})

(defn local-drpc-port []
  (local-drpc-port-config env))

(def max-spout-pending 1)

(def parallelism-config
  {:production
   {:add-to-feed 10
    :interest-reducer 3
    :seller-follows 5
    :collection-follows 6
    :follows 4
    :tag-likes 6
    :likes 4
    :stories max-spout-pending
    :active-users max-spout-pending
    :prepare-actions max-spout-pending
    :drpc-feed-builder 3}})

(defn parallelism [component]
  (or (get-in parallelism-config [env component]) 1))



(def admin-port 4055)
