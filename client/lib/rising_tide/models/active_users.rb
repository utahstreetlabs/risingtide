require 'ladon'

module RisingTide
  class ActiveUsers < RedisModel
    include Ladon::Logging

    SHARD_KEY = "sc"

    def self.active_user_ttl
      Config.active_user_ttl || (7 * 24 * 60 * 60)
    end

    def self.default_shard
      Config.default_shard || 1
    end

    def self.set_active!(user_id)
      with_redis do |redis|
        key = active_user_key(user_id)
        unless redis.expire(key, active_user_ttl)
          redis.hset(key, "sc", default_shard)
          redis.expire(key, active_user_ttl)
        end
      end
    end

    def self.active_user_key(user_id)
      format_key(:act, user_id)
    end

    def self.shard(user_id)
      key = active_user_key(user_id)
      with_redis do |redis|
        redis.hget(key, SHARD_KEY) || '1'
      end
    end

    class Config
      class << self
        attr_accessor :active_user_ttl, :default_shard
      end
    end
  end
end
