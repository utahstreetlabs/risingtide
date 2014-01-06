# Rising Tide

Rising Tide created customized feeds for users of Copious.com, an
online social marketplace active from 2011 to 2013.

Note that project setup instructions are unlikely to work as-is: the
source is being made available to the community for archaeological and
analytic purposes.

## Project Setup

```bash
git clone git@github.com:utahstreetlabs/risingtide.git
cd risingtide
git submodule init
git submodule update
cd actions-solr && make
gem install bundler
bundle install
foreman start
```

### Tests

```bash
bin/run-test-solr
lein midje
```


### Common maintenance tasks

### Get a production REPL

```bash
ec2-ssh $USERNAME@rt-storm1.copious.com
sudo su utah
cd ~/risingtide/current
RT_ENV=production bin/lein repl
```

### Run a report

```bash
ec2-ssh $USERNAME@rt-storm1.copious.com
sudo su utah
cd ~/risingtide/current
RT_ENV=production bin/lein run -m risingtide.reports/report
```

### Get a JMX term to examine metrics

```bash
RT_WORKER=2 #
ec2-ssh $USERNAME@rt-storm$RT_WORKER.copious.com
RT_EXECUTOR=0 # currently 0 or 1
java -jar /opt/jmxterm/jmxterm-1.0-alpha-4-uber.jar -l service:jmx:rmi:///jndi/rmi://localhost:1670$RT_EXECUTOR/jmxrmi -n
get -b default:name=seller-follow-interest-score-time,type=default OneMinuteRate
```

### Clear out old actions from solr

ec2-ssh $USERNAME@rt-storm1.copious.com
sudo su utah
cd ~/risingtide/current
RT_ENV=production bin/lein run -m risingtide.utils/flush-old-actions-from-solr

### Metrics

A raw list of metrics that we should compile into a more useful format:

```clj
(deftimer active-user-fetch-time)
(deftimer active-user-fanout-time)
(deftimer feed-load-time)
(deftimer expiration-time)
(deftimer feed-write-time)
(deftimer like-interest-score-time)
(deftimer follow-interest-score-time)
(deftimer seller-follow-interest-score-time)
(deftimer find-recent-actions-time)
(deftimer feed-build-time)

(defmeter action-saved "actions saved")
(defmeter action-created "actions created")
(defmeter action-processed "actions processed")
(defmeter action-processing-failed "actions processing attempts failed")
(defmeter expiration-run "expiration runs")
(defmeter feed-writes "feeds written")
(defmeter story-scored "stories scored")
(defmeter feed-builds "feeds built")
(defmeter curated-feed-writes "stories written to curated feed")

(gauge "active-user-count" (active-users redii))]
(gauge "feed-set-size" (count @feed-set))
(gauge "feed-set-feed-min-size" (apply max (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-max-size" (apply min (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-mean-size" (apply mean (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-median-size" (apply median (feed-set-feed-sizes @feed-set)))
(gauge "interest-reducer-size" (count @scores))]
(gauge "curated-feed-size" (count (seq @feed-atom)))

(defhistogram recent-actions-found)
```

#### Scout monitoring

Scout monitoring is configured with custom plugins. To deploy them to a new machine, run

    bin/copy-scout-plugins machine-name

Where machine-name is the subdomain of the host in question. So if you'd like to deploy scout
plugins to `rt-storm2.copious.com` run

    bin/copy-scout-plugins rt-storm2

HeapMemoryUsage,NonHeapMemoryUsage@java.lang:type=Memory



## License

Copyright © 2014 Utah Street Labs
