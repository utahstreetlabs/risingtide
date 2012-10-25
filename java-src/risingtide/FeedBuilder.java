package risingtide;

import backtype.storm.coordination.BatchOutputCollector;
import backtype.storm.drpc.LinearDRPCTopologyBuilder;
import backtype.storm.task.TopologyContext;
import backtype.storm.task.OutputCollector;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.topology.base.BaseBatchBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import java.util.Map;
import risingtide.feed.digest.DigestFeed;

public class FeedBuilder extends BaseBatchBolt {
    BatchOutputCollector _collector;
    Object _id;
    String _userId;
    DigestFeed _feed;
    String _userIdField;
    String _field;

    public FeedBuilder(String field, String userIdField) {
        _field = field;
        _userIdField = userIdField;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, BatchOutputCollector collector, Object id) {
        _collector = collector;
        _id = id;
        _feed = new DigestFeed(null);
    }

    @Override
    public void execute(Tuple tuple) {
        System.out.println("EXECUTING TUPLE");
        System.out.println(tuple.toString());
        System.out.println(tuple.getValueByField(_field));
        System.out.println("EXECUTED TUPLE");
        _userId = tuple.getStringByField(_userIdField);
        _feed = (DigestFeed)_feed.add(tuple.getValueByField(_field));
    }

    @Override
    public void finishBatch() {
        System.out.println("FINISHING!");
        System.out.println(_userId);
        System.out.println(_feed);
        System.out.println(_feed.idx);
        _collector.emit(new Values(_id, _userId, _feed));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("id", "user-id", "feed"));
    }
}
