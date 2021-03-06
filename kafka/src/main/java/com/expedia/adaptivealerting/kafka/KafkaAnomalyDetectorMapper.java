/*
 * Copyright 2018-2019 Expedia Group, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expedia.adaptivealerting.kafka;

import com.expedia.adaptivealerting.anomdetect.DetectorMapper;
import com.expedia.adaptivealerting.kafka.serde.JsonPojoSerde;
import com.expedia.adaptivealerting.kafka.util.DetectorUtil;
import com.expedia.metrics.MetricData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.util.stream.Collectors;

import static com.expedia.adaptivealerting.core.util.AssertUtil.notNull;

/**
 * Kafka Streams adapter for {@link DetectorMapper}.
 *
 * @author David Sutherland
 * @author Willie Wheeler
 */
@Slf4j
public final class KafkaAnomalyDetectorMapper extends AbstractStreamsApp {
    private static final String CK_AD_MAPPER = "ad-mapper";
    
    private final DetectorMapper mapper;
    
    public static void main(String[] args) {
        val config = new TypesafeConfigLoader(CK_AD_MAPPER).loadMergedConfig();
        val saConfig = new StreamsAppConfig(config);
        val detectorSource = DetectorUtil.buildDetectorSource(config);
        val mapper = new DetectorMapper(detectorSource);
        new KafkaAnomalyDetectorMapper(saConfig, mapper).start();
    }
    
    /**
     * Creates a new Kafka Streams adapter for the {@link DetectorMapper}.
     *
     * @param config Streams app configuration.
     * @param mapper Anomaly detector mapper.
     */
    public KafkaAnomalyDetectorMapper(StreamsAppConfig config, DetectorMapper mapper) {
        super(config);
        notNull(mapper, "mapper can't be null");
        this.mapper = mapper;
    }
    
    @Override
    protected Topology buildTopology() {
        val config = getConfig();
        val inboundTopic = config.getInboundTopic();
        val outboundTopic = config.getOutboundTopic();
        
        log.info("Initializing: inboundTopic={}, outboundTopic={}", inboundTopic, outboundTopic);
        
        val builder = new StreamsBuilder();
        final KStream<String, MetricData> stream = builder.stream(inboundTopic);
        stream
                .flatMap((key, metricData) -> {
                    log.trace("Mapping key={}, metricData={}", key, metricData);
                    val mappedMetricDataSet = mapper.map(metricData);
                    return mappedMetricDataSet.stream()
                            .map(mappedMetricData -> {
                                val newKey = mappedMetricData.getDetectorUuid().toString();
                                return KeyValue.pair(newKey, mappedMetricData);
                            })
                            .collect(Collectors.toSet());
                })
                // TODO Make outbound serde configurable. [WLW]
                .to(outboundTopic, Produced.with(new Serdes.StringSerde(), new JsonPojoSerde<>()));
        
        return builder.build();
    }
}
