# Rising Tide

Rising Tide is the Copious service for processing feed stories.

## Project Setup

# `git clone git@github.com:utahstreetlabs/risingtide.git`
# `cd risingtide`
# `git submodule init`
# `git submodule update`
# `cd actions-solr && make`
# `gem install bundler`
# `bundle install`
# `foreman start`

### Tests

# `bin/run-test-solr`
# `lein midje`


### Metrics

A raw list of metrics that we should compile into a more useful format:

(deftimer active-user-fetch-time)
(deftimer active-user-fanout-time)
(deftimer feed-load-time)
(deftimer expiration-time)
(deftimer feed-write-time)
(deftimer like-interest-score-time)
(deftimer follow-interest-score-time)
(deftimer seller-follow-interest-score-time)
(deftimer find-recent-actions-time)

(defmeter action-saved "actions saved")
(defmeter action-created "actions created")
(defmeter action-processed "actions processed")
(defmeter action-processing-failed "actions processing attempts failed")
(defmeter expiration-run "expiration runs")
(defmeter feed-writes "feeds written")
(defmeter story-scored "stories scored")

(gauge "active-user-count" (active-users redii))]
(gauge "feed-set-size" (count @feed-set))
(gauge "feed-set-feed-min-size" (apply max (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-max-size" (apply min (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-mean-size" (apply mean (feed-set-feed-sizes @feed-set)))
(gauge "feed-set-feed-median-size" (apply median (feed-set-feed-sizes @feed-set)))
(gauge "interest-reducer-size" (count @scores))]

(defhistogram recent-actions-found)

#### Scout monitoring

Scout monitoring is configured with custom plugins. To deploy them to a new machine, run

    bin/copy-scout-plugins machine-name

Where machine-name is the subdomain of the host in question. So if you'd like to deploy scout
plugins to `rt-storm2.copious.com` run

    bin/copy-scout-plugins rt-storm2

HeapMemoryUsage,NonHeapMemoryUsage@java.lang:type=Memory



## License

Copyright © 2012 Utah Street Labs
All Rights Reserved
