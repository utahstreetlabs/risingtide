require 'ladon'
require 'kaminari'
require 'storm/distributed_r_p_c'
require 'thrift_client'

module RisingTide
  class Feed < RedisModel
    include Ladon::Logging

    class << self
      attr_reader :feed_token

      def key(options = {})
        parts = [:f]
        if user_id = options[:interested_user_id]
          parts = parts + [:u, user_id]
        end
        parts << (options[:feed] || self.feed_token).to_s[0]
        format_key(parts)
      end

      # should be overridden by subclasses to support sharding
      def shard(options = {})
        nil
      end

      def with_redis(options = {})
        super({shard: self.shard(options)}.merge(options))
      end

      # Returns an ordered list of stories from a feed within the limits provide, both in terms of time and counts.
      #
      # @param [Hash] options
      # @option options [Integer] :interested_user_id the id of the user whose feed we should return
      # @option options [Integer] :offset the number of stories to skip
      # @option options [Integer] :limit the maximum number of documents to return
      # @option options [Time] :before only stories created before this time are considered
      # @option options [Time] :after only stories created after this time are considered
      # @return [Kaminari::PaginatableArray]
      def find_slice(options = {})
        offset = [options[:offset].to_i, 0].max
        limit = options[:limit].to_i
        limit = Kaminari.config.default_per_page unless limit > 0
        cnt = 0
        fkey, before, after = common_options(options)

        values = with_redis(options.merge(default_data: [])) do |redis|
          cnt = count(options)
          benchmark("Fetch values for #{fkey} #{after} #{before} #{offset} #{limit}") do
            if (before || after)
              redis.zrevrangebyscore(fkey, before, after, withscores: true, limit: [offset, limit])
            else
              redis.zrevrange(fkey, offset, (offset + limit - 1), withscores: true)
            end
          end
        end

        # XXX: redis-rb introduced a change to the client that's going to break this whenever we upgrade
        # (they are doing this map inside the client, instead of returning the raw array)
        stories = values.each_slice(2).map {|s,t| Story.decode(s,t)}
        Kaminari::PaginatableArray.new(stories, limit: limit, offset: offset, total_count: cnt)
      end

      def count(options = {})
        fkey, before, after = common_options(options)
        benchmark("Get Cardinality for #{fkey} #{after} #{before}") do
          with_redis(options) { |r| (before || after) ? r.zcount(fkey, after, before) : r.zcard(fkey) }
        end
      end

      def common_options(options = {})
        fkey = self.key(options)
        before = options[:before]
        after = options[:after]
        if (before || after)
          # zrevrangebyscore is inclusive, so we offset by 1 second on each end to get exclusive behavior
          before = before ? (before - 1) : :inf
          after = after ? (after + 1 ) : 0
        end
        [fkey, before, after]
      end
    end
  end

  class CardFeed < Feed
    class << self
      attr_accessor :feed_build_service
    end

    def self.shard_number(user_id)
      RisingTide::ActiveUsers.shard(user_id)
    end

    def self.feed_token; :c; end

    def self.shard_config_bucket; format_key 'card-feed-shard-config'; end

    def self.shard_key(user_id)
      RisingTide::ShardConfig.shard_key(shard_config_bucket, user_id)
    end

    def self.shard(options = {})
      if options[:interested_user_id]
        "feed_#{shard_number(options[:interested_user_id])}".to_sym
      else
        :everything_card_feed
      end
    end

    def self.build(user_id)
      feed_build_service.build(user_id.to_s)
    end
  end

  module FeedBuild
    class StormService
      attr_reader :host, :port

      def initialize(servers, timeout = 2, retries = 2)
        @servers = servers
        @timeout = timeout
        @retries = retries
      end

      def feed_build_client
        @feed_build_client ||= ThriftClient.new(Storm::DistributedRPC::Client, @servers, timeout: @timeout, retries: @retries)
      end

      def build(user_id)
        json = feed_build_client.execute('build-feed', user_id.to_s)
        Yajl::Parser.new.parse(json).map {|h| Story.from_hash(h) }
      end
    end

    class TestService
      include Ladon::Logging
      def build(user_id)
        logger.info('ignoring feed build request for user ', user_id)
        []
      end
    end
  end
end
