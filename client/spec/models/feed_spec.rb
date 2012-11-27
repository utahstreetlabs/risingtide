require 'spec_helper'
require 'rising_tide/models/feed'

describe RisingTide::Feed do
  let(:active_users_redis) { stub_redis(RisingTide::ActiveUsers, {}) }
  let(:card_redis) { stub_redis(RisingTide::CardFeed, {everything_card_feed: {}, card_feed_1: {}}) }
  let(:user_id) { 10 }

  def expects_card_shard_key_lookup
    active_users_redis.expects(:hget).with(RisingTide::ActiveUsers.active_user_key(user_id), RisingTide::ActiveUsers::SHARD_KEY).returns(nil)
  end

  describe '#find_slice' do
    let(:story_hashes) do
      [{t: :listing_activated, lid: 1, aid: 2}, {t: :listing_liked, lid: 3, aid: 4},
       {t: :listing_sold, lid: 5, aid: 6}, {t: :tag_liked, tid: 7, aid: 8}]
    end

    def days_ago(days)
      Time.now - (24 * 60 * 60)
    end

    let(:stories) { story_hashes.each_with_index.flat_map {|h,i| [Yajl::Encoder.encode(h), days_ago(i)]} }

    context 'with no interested user id specified' do
      let(:key) { "magt:f:c" }

      it 'should fetch stories from the everything feed' do
        card_redis.expects(:zcard).with(key).returns(stories.count)
        card_redis.expects(:zrevrange).with(key, 0, 1, withscores: true).returns(stories[0..3])
        result = RisingTide::CardFeed.find_slice(offset: 0, limit: 2)
        result.should have(2).stories
        result.first.should == RisingTide::Story.decode(*stories[0..1])
      end

      context 'with time boundaries specified' do
        let(:before) { days_ago(1.5) }
        let(:redis_before) { before - 1 }
        let(:after) { days_ago(2.5) }
        let(:redis_after) { after + 1 }

        it 'should only return stories after the :after parameter' do
          card_redis.expects(:zcount).with(key, redis_after, :inf).returns(3)
          card_redis.expects(:zrevrangebyscore).with(key, :inf, redis_after, withscores: true, limit: [0, 10]).
            returns(stories[0..5])
          result = RisingTide::CardFeed.find_slice(after: after, offset: 0, limit: 10)
          result.should have(3).stories
          result.first.should == RisingTide::Story.decode(*stories[0..1])
        end

        it 'should only return stories before the :before parameter' do
          card_redis.expects(:zcount).with(key, 0, redis_before).returns(2)
          card_redis.expects(:zrevrangebyscore).with(key, redis_before, 0, withscores: true, limit: [0, 10]).
            returns(stories[4..7])
          result = RisingTide::CardFeed.find_slice(before: before, offset: 0, limit: 10)
          result.should have(2).stories
          result.first.should == RisingTide::Story.decode(*stories[4..5])
        end

        it 'should respect both the :before and :after parameter when combined' do
          card_redis.expects(:zcount).with(key, redis_after, redis_before).returns(1)
          card_redis.expects(:zrevrangebyscore).with(key, redis_before, redis_after, withscores: true, limit: [0, 10]).
            returns(stories[4..5])
          result = RisingTide::CardFeed.find_slice(before: before, after: after, offset: 0, limit: 10)
          result.should have(1).story
          result.first.should == RisingTide::Story.decode(*stories[4..5])
        end
      end
    end

    context 'with an interested user id specified' do
      let(:key) { "magt:f:u:#{user_id}:c" }

      # XXX: this is basically the same test as above with different context.  how to not repeat?
      it 'should fetch stories for the specified user' do
        expects_card_shard_key_lookup.twice
        card_redis.expects(:zcard).with(key).returns(stories.count)
        card_redis.expects(:zrevrange).with(key, 0, 1, withscores: true).returns(stories[0..3])
        result = RisingTide::CardFeed.find_slice(interested_user_id: user_id, offset: 0, limit: 2)
        result.should have(2).stories
        result.first.should == RisingTide::Story.decode(*stories[0..1])
      end
    end

    context 'when an error occurs' do
      let(:key) { "magt:f:c" }

      it 'should return the default data' do
        card_redis.expects(:zcard).raises(Exception.new('explosions!'))
        result = RisingTide::CardFeed.find_slice
        result.should have(0).stories
      end
    end
  end

  describe '#count' do
    context 'with an interested user id specified' do
      let(:user_id) { 10 }
      let(:key) { "magt:f:u:#{user_id}:c" }
      let(:count) { 7 }

      context 'with no timeslicing' do
        it 'should return the count of new listings for the user' do
          expects_card_shard_key_lookup
          card_redis.expects(:zcard).with(key).returns(count)
          result = RisingTide::CardFeed.count(interested_user_id: user_id)
          result.should == count
        end
      end

      context 'with a before parameter' do
        let(:before) { 1334457543 }
        it 'should return the count of new listings for the user before that time' do
          expects_card_shard_key_lookup
          card_redis.expects(:zcount).with(key, 0, before - 1).returns(count)
          result = RisingTide::CardFeed.count(interested_user_id: user_id, before: before)
          result.should == count
        end
      end
    end
  end
end
