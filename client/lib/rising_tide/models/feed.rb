require 'ladon'
require 'kaminari'

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
        count = 0
        before = options[:before]
        after = options[:after]
        if timeslice = (before || after)
          # zrevrangebyscore is inclusive, so we offset by 1 millisecond on each end to get exclusive behavior
          before = before ? (before - 1) : :inf
          after = after ? (after + 1 ) : 0
        end

        values = with_redis do |redis|
          fkey = self.key(options)
          count = benchmark("Get Cardinality for #{fkey} #{after} #{before}") { timeslice ? redis.zcount(fkey, after, before) : redis.zcard(fkey) }
          benchmark("Fetch values for #{fkey} #{after} #{before} #{offset} #{limit}") do
            if timeslice
              redis.zrevrangebyscore(fkey, before, after, withscores: true, limit: [offset, limit])
            else
              redis.zrevrange(fkey, offset, (offset + limit - 1), withscores: true)
            end
          end
        end

        # XXX: redis-rb introduced a change to the client that's going to break this whenever we upgrade
        # (they are doing this map inside the client, instead of returning the raw array)
        stories = values.each_slice(2).map {|s,t| Story.decode(s,t)}
        Kaminari::PaginatableArray.new(stories, limit: limit, offset: offset, total_count: count)
      end
    end
  end

  class CardFeed < Feed
    def self.feed_token; :c; end
  end

  class NetworkFeed < Feed
    def self.feed_token; :n; end
  end
end
