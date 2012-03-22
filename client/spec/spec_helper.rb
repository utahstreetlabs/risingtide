require 'rubygems'
require 'bundler'
require 'rising_tide/models/redis_model'

# Bundler >= 1.0.10 uses Psych YAML, which is broken, so fix that.
# https://github.com/carlhuda/bundler/issues/1038
YAML::ENGINE.yamler = 'syck'

Bundler.setup

require 'mocha'
require 'rspec'

RSpec.configure do |config|
  config.mock_with :mocha
end

pattern = File.join(Dir.pwd, 'spec/support/**/*.rb')
Dir[pattern].each {|f| require f}

RisingTide::RedisModel.environment = 'test'
