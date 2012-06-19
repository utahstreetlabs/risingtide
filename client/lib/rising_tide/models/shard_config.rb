require 'ladon'

module RisingTide
  class ShardConfig < RedisModel
    include Ladon::Logging

    class << self
      def shard_key(user_id)
        with_redis do |redis|
          redis.hget("card-feed-shard-config", user_id) || '1'
        end
      end
    end
  end
end
