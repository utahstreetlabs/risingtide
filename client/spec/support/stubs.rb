require 'rising_tide/models/redis_model'
require 'rising_tide/models/feed'
require 'rising_tide/models/story'

module Stubs
  def stub_redis
    [RisingTide::RedisModel, RisingTide::Story, RisingTide::CardFeed, RisingTide::NetworkFeed].each do |klazz|
      klazz.redis = nil
      klazz.stubs(:config).returns(stub_everything('redis config'))
    end
    redis = stub('redis', client: stub(connected?: true))
    Redis.expects(:new).returns(redis)
    redis
  end


end

RSpec.configure do |config|
  config.include Stubs
  config.before do
    Airbrake.stubs(:notify)
  end
end
