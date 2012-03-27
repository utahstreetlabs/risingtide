require 'spec_helper'
require 'rising_tide/models/feed'

describe RisingTide::Feed do
  describe '#find_slice' do
    let(:story_hashes) do
      [{t: :listing_activated, lid: 1, aid: 2}, {t: :listing_liked, lid: 3, aid: 4},
       {t: :listing_sold, lid: 5, aid: 6}, {t: :tag_liked, tid: 7, aid: 8}]
    end
    let(:stories) { story_hashes.each_with_index.flat_map {|h,i| [Yajl::Encoder.encode(h), i.days.ago.to_i]} }
    let(:redis) { stub_redis }

    context 'with no interested user id specified' do
      let(:key) { "magt:f:c" }

      it 'should fetch stories from the everything feed' do
        redis.expects(:zcard).with(key).returns(stories.count)
        redis.expects(:zrevrange).with(key, 0, 1, withscores: true).returns(stories[0..3])
        result = RisingTide::CardFeed.find_slice(offset: 0, limit: 2)
        result.should have(2).stories
        result.first.should == RisingTide::Story.decode(*stories[0..1])
      end

      context 'with time boundaries specified' do
        let(:before) { 1.5.days.ago.to_i }
        let(:redis_before) { before - 1 }
        let(:after) { 2.5.days.ago.to_i }
        let(:redis_after) { after + 1 }

        it 'should only return stories after the :after parameter' do
          redis.expects(:zcount).with(key, redis_after, :inf).returns(3)
          redis.expects(:zrevrangebyscore).with(key, :inf, redis_after, withscores: true, limit: [0, 10]).
            returns(stories[0..5])
          result = RisingTide::CardFeed.find_slice(after: after, offset: 0, limit: 10)
          result.should have(3).stories
          result.first.should == RisingTide::Story.decode(*stories[0..1])
        end

        it 'should only return stories before the :before parameter' do
          redis.expects(:zcount).with(key, 0, redis_before).returns(2)
          redis.expects(:zrevrangebyscore).with(key, redis_before, 0, withscores: true, limit: [0, 10]).
            returns(stories[4..7])
          result = RisingTide::CardFeed.find_slice(before: before, offset: 0, limit: 10)
          result.should have(2).stories
          result.first.should == RisingTide::Story.decode(*stories[4..5])
        end

        it 'should respect both the :before and :after parameter when combined' do
          redis.expects(:zcount).with(key, redis_after, redis_before).returns(1)
          redis.expects(:zrevrangebyscore).with(key, redis_before, redis_after, withscores: true, limit: [0, 10]).
            returns(stories[4..5])
          result = RisingTide::CardFeed.find_slice(before: before, after: after, offset: 0, limit: 10)
          result.should have(1).story
          result.first.should == RisingTide::Story.decode(*stories[4..5])
        end
      end
    end

    context 'with an interested user id specified' do
      let(:user_id) { 10 }
      let(:key) { "magt:f:u:#{user_id}:c" }

      # XXX: this is basically the same test as above with different context.  how to not repeat?
      it 'should fetch stories for the specified user' do
        redis.expects(:zcard).with(key).returns(stories.count)
        redis.expects(:zrevrange).with(key, 0, 1, withscores: true).returns(stories[0..3])
        result = RisingTide::CardFeed.find_slice(interested_user_id: user_id, offset: 0, limit: 2)
        result.should have(2).stories
        result.first.should == RisingTide::Story.decode(*stories[0..1])
      end
    end
  end
end
