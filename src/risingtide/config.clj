(ns risingtide.config)

(def redis
  {:development {:resque {}
                 :everything-card-feed {}
                 :card-feeds-1 {}
                 :card-feeds-2 {:db 1}
                 :network-feeds {} :interests {} :stories {}
                 :shard-config {}}
   :test {:resque {}
          :everything-card-feed {} :card-feeds-1 {}
          :network-feeds {} :interests {} :stories {}
          :shard-config {}}
   :staging {:resque {:host "staging3.copious.com"}
             :everything-card-feed {:host "staging4.copious.com"}
             :card-feeds-1 {:host "staging4.copious.com"}
             :network-feeds {:host "staging4.copious.com"}
             :interests {:host "staging4.copious.com"}
             :stories {:host "staging4.copious.com"}
             :shard-config {:host "staging4.copious.com"}}
   :demo {:resque {:host "demo1.copious.com"}
          :everything-card-feed {:host "demo1.copious.com"}
          :card-feeds-1 {:host "demo1.copious.com"}
          :network-feeds {:host "demo1.copious.com"}
          :interests {:host "demo1.copious.com"}
          :stories {:host "demo1.copious.com"}
          :shard-config {:host "demo1.copious.com"}
          }
   :production {:resque {:host "resque-redis-master.copious.com"}
                :everything-card-feed {:host "rt-card-feeds-redis.copious.com"}
                :card-feeds-1 {:host "rt-card-feeds-redis.copious.com"}
                :network-feeds {:host "rt-network-feeds-redis.copious.com"}
                :interests {:host "rt-interests-redis.copious.com"}
                :stories {:host "rt-stories-redis.copious.com"}
                :shard-config {:host "rt-shard-config-redis.copious.com"}}})

(def digest
  {:development true
   :staging true
   :production true})

(def max-card-feed-size 1000)
(def max-network-feed-size 250)
(def single-actor-digest-story-min 15)
(def default-card-shard "1")

(def ports {:admin 4050
            :mycroft 4055})
