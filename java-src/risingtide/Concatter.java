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

public class Concatter extends BaseBatchBolt {
    BatchOutputCollector _collector;
    Object _id;
    StringBuffer _strings = new StringBuffer();
    String _field;

    public Concatter(String field) {
        _field = field;
    }

    @Override
    public void prepare(Map conf, TopologyContext context, BatchOutputCollector collector, Object id) {
        _collector = collector;
        _id = id;
    }

    @Override
    public void execute(Tuple tuple) {
        System.out.println("EXECUTING TUPLE");
        System.out.println(tuple.toString());

        _strings.append(tuple.getValueByField(_field).toString());
    }

    @Override
    public void finishBatch() {
        System.out.println("FINISHING!");
        System.out.println(_strings.toString());
        _collector.emit(new Values(_id, _strings.toString()));
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("id", "string"));
    }

    // @Override
    // public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
    //     //        _collector = collector;
    // }
    // @Override
    // public void cleanup() {
    // }
}
