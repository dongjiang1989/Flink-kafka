/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka;

import java.util.*;

import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.functions.FoldFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer08;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer08;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

	public static void main(String[] args) throws Exception {
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime.EventTime);
		env.enableCheckpointing(1000);
		env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

		final Properties KafkaProps = new Properties();

		KafkaProps.setProperty("zookeeper.connect","127.0.0.1:2181");
		KafkaProps.setProperty("bootstrap.servers","127.0.0.1:9092");
		KafkaProps.setProperty("group.id","test");

		List<String> supplierNames = Arrays.asList("127.0.0.1:9092");

		DataStream<String> stream = env.addSource(
				new FlinkKafkaConsumer08<>("wiki-result", new SimpleStringSchema(), KafkaProps)
		);

		stream.map(new MapFunction<String, String>() {
			private static final long serialVersionUID = -6867736771747690202L;

			public String map(String value) throws Exception {
				return "Stream Value: " + value;
			}
		}).print();

		KeyedStream<String, String> keyedEdits = stream
				.keyBy(new KeySelector<String, String>() {
					@Override
					public String getKey(String event) {
						return event.toUpperCase();
					}
				});

		DataStream<Tuple2<String, Long>> result = keyedEdits
				.timeWindow(Time.seconds(60))
				.fold(new Tuple2<>("", 0L), new FoldFunction<String, Tuple2<String, Long>>() {
					@Override
					public Tuple2<String, Long> fold(Tuple2<String, Long> acc, String event) {
						acc.f0 = event.toLowerCase();
						acc.f1 += event.length();
						return acc;
					}
				});

		result.map(new MapFunction<Tuple2<String, Long>, String>() {
			@Override
			public String map(Tuple2<String, Long> tuple) {
				return tuple.toString();
			}
		}).addSink(new FlinkKafkaProducer08<>("localhost:9092", "wiki-result", new SimpleStringSchema()));

		env.execute("Flink Streaming Java API Skeleton");
	}
}
