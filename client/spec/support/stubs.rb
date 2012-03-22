require 'rising_tide/models/redis_model'

module Stubs
  def stub_redis
    RisingTide::RedisModel.redis = nil
    RisingTide::Story.stubs(:redis_config).returns(stub_everything('redis config'))
    RisingTide::CardFeed.stubs(:redis_config).returns(stub_everything('redis config'))
    redis = stub('redis', client: stub(connected?: true))
    Redis.expects(:new).returns(redis)
    redis
  end
end

RSpec.configure do |config|
  config.include Stubs
end
