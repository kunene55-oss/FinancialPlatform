package com.example.financial.ingestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.*;
import com.example.financial.ingestion.dto.TransactionReceivedEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;
import java.util.HashMap;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, TransactionReceivedEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TransactionReceivedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}