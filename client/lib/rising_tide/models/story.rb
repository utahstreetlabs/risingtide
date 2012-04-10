require 'rising_tide/models/redis_model'

module RisingTide
  class Story < RedisModel
    # map to uncompress keys from json storage
    REVERSE_ATTRIBUTE_MAP = {
      a: :action,
      t: :type,
      ts: :types,
      aid: :actor_id,
      aids: :actor_ids,
      lid: :listing_id,
      lids: :listing_ids,
      tid: :tag_id,
      bid: :buyer_id,
      fid: :followee_id,
      iid: :invitee_profile_id,
      tx: :text,
      n: :network
    }
    # created_at is stored separately as the 'score'
    attr_accessor *([:created_at].concat(REVERSE_ATTRIBUTE_MAP.values))

    def ==(other)
      [:type, :actor_id, :listing_id, :tag_id].all? {|k| self.send(k) == other.send(k)}
    end

    def type=(type)
      @type = type.to_sym
    end

    class << self
      def decode(encoded, timestamp = 0)
        story = self.new
        Yajl::Parser.new.parse(encoded).each do |key,value|
          if REVERSE_ATTRIBUTE_MAP[key.to_sym]
            story.send("#{REVERSE_ATTRIBUTE_MAP[key.to_sym]}=", (key == 't') ? value.to_sym : value)
          end
        end
        story.created_at = Time.at(timestamp.to_i)
        story
      end

      def find_most_recent(options={})
        RisingTide::Feed.find_slice(options)
      end

      # Returns the most recent stories for each identified listing.
      #
      # @param [Array] listing_ids the ids of the listings to find stories for
      # @param [Hash] options
      # @option options [Integer] :limit (1) the maximum number of stories to return for each listing
      # @option options [Integer] :interested_user_id when provided, prefers stories which this user finds interesting
      # @return [Hash] a lookup table of listing id to stories
      def find_most_recent_for_listings(listing_ids, options = {})
        limit = options[:limit] || 1
        with_redis do |redis|
          if user_id = options[:interested_user_id]
            interesting_listings = Set.new(redis.smembers(interest_key(user_id, :l)).map {|m| m.split(':').last.to_i})
            interesting_actors = Set.new(redis.smembers(interest_key(user_id, :a)).map {|m| m.split(':').last.to_i})

            listing_ids.each_with_object({}) do |id,map|
              key = format_key(:c, :l, id)
              # if the listing is interesting, or there is no chance of finding an interesting actor, just load the
              # most recent stories
              if interesting_listings.member?(id) || interesting_actors.count == 0
                map[id] = redis.zrevrange(key, 0, limit - 1).map {|s| decode(s)}
              else
                # otherwise, we want to find the most recent story from an interesting actor, which means we need
                # to load all the stories and search through them
                stories = redis.zrevrange(key, 0, -1)
                # searching optimized for a limit of 1, because i can't find anywhere we use any other limit
                if limit == 1
                  story = stories.find(lambda {stories.first}) {|s| interesting_actors.member?(decode(s).actor_id)}
                  map[id] = [decode(story)] if story
                else
                  preferred, other = stories.map {|s| decode(s)}.partition do |story|
                    interesting_actors.member?(story.actor_id)
                  end
                  map[id] = preferred.concat(other).slice(0, limit)
                end
              end
            end
          else
            # if there's no user to worry about, just return the most recent :limit story/stories for each listing
            listing_ids.each_with_object({}) do |id, map|
              map[id] = redis.zrevrange(format_key(:c, :l, id), 0, limit - 1).map {|s| decode(s)}
            end
          end
        end
      end
    end
  end
end
