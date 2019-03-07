package com.github.arpitmsharma;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.IterativeStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Map;
import java.util.Properties;

@SpringBootApplication
public class FlinkCepDemo {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<String> temperatureMessageStream = env.addSource(new FlinkKafkaConsumer010<>("temperature-event", new SimpleStringSchema(), getProperties()));
        temperatureMessageStream.print();
        ObjectMapper mapper = new ObjectMapper();
        DataStream<TemperatureEvent> temperatureEventStream = temperatureMessageStream.map(str -> mapper.readValue(str, TemperatureEvent.class));
//        IterativeCondition<TemperatureEvent> condition = new SimpleCondition<TemperatureEvent>() {
//            @Override
//            public boolean filter(TemperatureEvent event) throws Exception {
//                if (event.getValue() > 20) {
//                    return false;
//                }
//                return true;
//            }
//        };
//        Pattern pattern = Pattern.<TemperatureEvent>begin("temp-event").where(condition);
        // PatternStream<TemperatureEvent> patternStream = CEP.pattern(temperatureEventStream, pattern);
        DataStream<String> ruleMessageStream = env.addSource(new FlinkKafkaConsumer010<>("rule-event", new SimpleStringSchema(), getProperties()));
        DataStream<Rule> ruleStream = ruleMessageStream.map(str -> mapper.readValue(str, Rule.class));
        DataStream<Pattern> rulePatternStream = ruleStream.map(rule -> createPatternFromRule(rule));

        DataStream<PatternStream<TemperatureEvent>> patternStreamStream = rulePatternStream.map(rulePattern -> CEP.pattern(temperatureEventStream, rulePattern));
        DataStream<DataStream<String>> alertStream = patternStreamStream.map(patternStream -> patternStream.select(new PatternSelectFunction<TemperatureEvent, String>() {
            @Override
            public String select(Map<String, List<TemperatureEvent>> pattern) throws Exception {
                List<TemperatureEvent> list = pattern.get("temp-event");
                StringBuilder sb = new StringBuilder("Alert!! ");
                for (TemperatureEvent te : list) {
                    sb.append("  " + te.getValue() + "  ");
                }
                return sb.toString();
            }
        }));
        IterativeStream<DataStream<String>> alertStreamItr = alertStream.iterate();
        alertStreamItr.addSink(new PrintSinkFunction<DataStream<String>>(){
        });
        env.execute();
        SpringApplication.run(FlinkCepDemo.class, args);
    }

    private static Pattern createPatternFromRule(Rule rule) {
        IterativeCondition<TemperatureEvent> condition = new SimpleCondition<TemperatureEvent>() {
            @Override
            public boolean filter(TemperatureEvent event) throws Exception {
                if (rule.getValue() > event.getValue()) {
                    return true;
                }
                return false;
            }
        };

        Pattern pattern = Pattern.<TemperatureEvent>begin("temp-event").where(condition);
        return pattern;
    }

    private static Properties getProperties() {
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "test");

        return properties;
    }


}
