# risingtide

Rising Tide processes stories into feeds.

## Usage

To run the job processor from the command line, do:

bin/lein run

## Development

## Testing

We use [Midje](https://github.com/marick/Midje) to test Rising Tide

## Utilities

# Build watcher indexes from interest indexes

    lein run :build-watcher-indexes

# Verify all interests exist in watcher indexes

    lein run :check-interest-coherence

# Verify all watchers exist in interest indexes

    lein run :check-watcher-coherence

# Convert redis keys from staging to dev

After importing a redis dump from staging, you'll want to run this to
get correct key prefixes

    lein run :convert-redis-keys-from-staging-to-dev

## Design

Rising Tide is designed to be a fast, parallelizable story processor
and feed digester. It pulls stories off a resque queue, stores them in
a temporary cache, and then rebuilds and digests the last 24 hours of
all interested feeds. It also processes new user interests

## License

Copyright (C) 2012 Utah Street Labs

All rights reserved.
