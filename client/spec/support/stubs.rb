require 'rising_tide/models/redis_model'
require 'rising_tide/models/shard_config'
require 'rising_tide/models/feed'
require 'rising_tide/models/story'

module Stubs
  def stub_redis(klazz, config)
    klazz.stubs(:config).returns(config)
    klazz.reset_redii()
    redis = stub("#{klazz} redis", client: stub(connected?: true))
    Redis.stubs(:new).returns(redis)
    klazz.with_redis {}
    redis
  end
end

RSpec.configure do |config|
  config.include Stubs
  config.before do
    Airbrake.stubs(:notify)
  end
end
