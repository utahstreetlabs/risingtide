require 'ladon'

module RisingTide
  class ActiveUsers < RedisModel
    include Ladon::Logging

    class << self
      attr_accessor :active_user_ttl
    end

    def self.add_active(user_id)
      with_redis do |redis|
        key = active_user_key(user_id)
        redis.set(key)
        redis.expire(key, active_user_ttl)
      end
    end

    def self.active_user_key(user_id)
      format_key(:act, user_id)
    end
  end
end
