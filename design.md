new digesting:

iterate through feed, find digest stories that could take story

if none, find individual stories that could be digested with it

if none, insert


---

* use existing caching aggregator [modified to use digesting stories]
* insert a new story into it


---------------------------------------


1 story comes in
2 story is inserted into redis

3 interested feeds are computed

4 story is inserted into digesting cache

5 interested feeds are rebuilt



race condition when one worker reads the feed but it gets updated out from underneath it



shared state:

- multiple workers, each with their own digesting caches
-




1 story comes in
2 story is inserted into redis
3 watcher sets are used to determine interested feeds
4 in-memory feed datastructure is updated and marked dirty

other thread

1 loop through all dirty feeds in datastructure and save them out to redis
2 wait 10 seconds