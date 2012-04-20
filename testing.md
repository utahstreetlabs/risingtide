
stories 6380
interests 6381
network-feeds 6382
card-feeds 6383
resque 6384


ssh -L 6380:0.0.0.0:6380 etest2.copious.com -N &
ssh -L 6381:0.0.0.0:6381 etest2.copious.com -N &
ssh -L 6382:0.0.0.0:6382 etest2.copious.com -N &
ssh -L 6383:0.0.0.0:6383 etest2.copious.com -N &
ssh -L 6384:0.0.0.0:6384 etest2.copious.com -N &

s3cmd ls s3://utahstreetlabs.com/backups/redis/mag_redis/
s3cmd get s3://utahstreetlabs.com/backups/redis/mag_redis/dump120420160001.rdb.gz network-feeds/dump.rdb.gz --force; gunzip network-feeds/dump.rdb.gz -f
s3cmd get s3://utahstreetlabs.com/backups/redis/card_feeds/dump120420160001.rdb.gz card-feeds/dump.rdb.gz --force; gunzip card-feeds/dump.rdb.gz -f
s3cmd get s3://utahstreetlabs.com/backups/redis/stories/dump120420160001.rdb.gz stories/dump.rdb.gz --force; gunzip stories/dump.rdb.gz -f
s3cmd get s3://utahstreetlabs.com/backups/redis/interests/dump120420160001.rdb.gz interests/dump.rdb.gz --force; gunzip interests/dump.rdb.gz -f
redis-server resque.conf &
redis-server stories.conf &
redis-server interests.conf &
redis-server network-feeds.conf &
redis-server card-feeds.conf &

redis-cli -h test2.copious.com -p 6380 ping
redis-cli -h test2.copious.com -p 6381 ping
redis-cli -h test2.copious.com -p 6382 ping
redis-cli -h test2.copious.com -p 6383 ping
redis-cli -h test2.copious.com -p 6384 ping


sudo add-apt-repository ppa:ferramroberto/java
sudo apt-get update
sudo apt-get install sun-java6-jre sun-java6-plugin sun-java6-fonts


127.0.0.1 localhost.localdomain localhost resque-redis-master.copious.com rt-card-feeds-redis.copious.com rt-network-feeds-redis.copious.com rt-interests-redis.copious.com rt-stories-redis.copious.com
rt-card-feeds-redis.copious.com rt-network-feeds-redis.copious.com rt-interests-redis.copious.com rt-stories-redis.copious.com
