require 'ladon'

module RisingTide
  class ShardConfig < RedisModel
    include Ladon::Logging

    class << self
      def shard_key(bucket, user_id)
        with_redis do |redis|
          redis.hget(bucket, user_id) || '1'
        end
      end
    end
  end
end
