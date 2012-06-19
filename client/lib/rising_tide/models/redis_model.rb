require 'ladon'
require 'redis'

module RisingTide
  class RedisModel
    include Ladon::ErrorHandling

    class << self
      attr_accessor :config
      attr_accessor :environment
      @environment ||= 'development'
      def namespace
        @namespace ||= :"mag#{(self.environment)[0]}"
      end

      def redii
        @redii ||= {}
      end

      def reset_redii
        @redii = {}
      end
    end

    def with_redis(&block)
      RedisModel.with_redis(&block)
    end

    def format_key(*parts)
      RedisModel.format_key(*parts)
    end

    def self.format_key(*parts)
      parts.insert(0, RedisModel.namespace)
      parts.join(':')
    end

    def self.user_key(user_id)
      format_key(:n, :u, user_id)
    end

    def self.interest_key(user_id, type)
      format_key(:i, :u, user_id, type)
    end

    def self.feed_key(type, user_id = nil)
      parts = [:f]
      parts.concat([:u, user_id]) if user_id
      parts << type
      format_key(*parts)
    end

    def self.everything_feed_key
      self.feed_key(:c)
    end

    def self.with_redis(options={}, &block)
      # use long-lived connections to redis, but reconnect if the connection has been lost
      # XXX: this approach is not threadsafe, designed specifically for use within unicorn (or other non-threaded,
      # forked servers)
      begin
        config = self.config
        shard_key = options[:shard]
        config = config[shard_key] if shard_key
        redii[shard_key] = Redis.new(config) unless redii[shard_key] && redii[shard_key].client.connected?

        block.call(redii[shard_key])
      rescue Exception => e
        handle_error('Redis Connection', e)
        options[:default_data] or nil
      end
    end

    def self.benchmark(description, &block)
      t1 = Time.now
      rv = yield
      t2 = Time.now
      elapsed = sprintf("%0.2f", (t2-t1)*1000)
      logger.info "#{description}: #{elapsed} ms" if logger
      rv
    end
  end
end
