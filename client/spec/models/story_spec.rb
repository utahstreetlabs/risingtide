require 'spec_helper'
require 'rising_tide/models/story'
require 'yajl'

describe RisingTide::Story do
  describe '#decode' do
    let(:type) { :listing_liked }
    let(:actor_id) { 10 }
    let(:listing_id) { 100 }
    let(:time) { Time.new(2012, 1, 1) }
    let(:encoded) { Yajl::Encoder.encode({ t: type, aid: actor_id, lid: listing_id }) }
    subject { RisingTide::Story.decode(encoded, time.to_i) }
    its(:type) { should == type }
    its(:actor_id) { should == actor_id }
    its(:listing_id) { should == listing_id }
    its(:created_at) { should == time }
  end

  describe '#find_most_recent_for_listings' do
    let(:listing_ids) { [123, 456] }
    let(:responses) do
      listing_ids.map {|id| 3.times.map {|i| Yajl::Encoder.encode({t: :listing_liked, lid: id, aid: i})}}
    end
    let(:redis) { stub_redis(RisingTide::Story, {}) }

    it 'should return the most recent stories for the specified listings' do
      redis.expects(:smembers).with('magt:i:u:1:l').never
      redis.expects(:smembers).with('magt:i:u:1:a').never
      redis.expects(:zrevrange).with("magt:c:l:#{listing_ids.first}", 0, 0).returns([responses.first.first])
      redis.expects(:zrevrange).with("magt:c:l:#{listing_ids.last}", 0, 0).returns([responses[1].first])
      actual = RisingTide::Story.find_most_recent_for_listings(listing_ids)
      actual.should have(listing_ids.size).stories
      actual[listing_ids.first].first.listing_id.should == listing_ids.first
      actual[listing_ids.first].first.actor_id.should == 0
      actual[listing_ids.last].first.listing_id.should == listing_ids.last
      actual[listing_ids.last].first.actor_id.should == 0
    end

    it 'should select interesting stories when a user is specified' do
      redis.expects(:smembers).with('magt:i:u:1:l').returns([])
      redis.expects(:smembers).with('magt:i:u:1:a').returns(['a:2'])
      redis.expects(:zrevrange).with("magt:c:l:#{listing_ids.first}", 0, -1).returns(responses.first)
      redis.expects(:zrevrange).with("magt:c:l:#{listing_ids.last}", 0, -1).returns(responses.last)
      actual = RisingTide::Story.find_most_recent_for_listings(listing_ids, interested_user_id: 1, limit: 1)
      actual.should have(listing_ids.size).stories
      actual[listing_ids.first].first.listing_id.should == listing_ids.first
      actual[listing_ids.first].first.actor_id.should == 2
      actual[listing_ids.last].first.listing_id.should == listing_ids.last
      actual[listing_ids.last].first.actor_id.should == 2
    end
  end
end
