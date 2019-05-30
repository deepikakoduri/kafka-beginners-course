package com.github.deepikakoduri.kafka.tutorial1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {

    private Logger logger;

    private ConsumerDemoWithThread() {
        logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class);
    }

    public static void main(String[] args) {
        new ConsumerDemoWithThread().run();
    }

    private void run() {
        CountDownLatch latch = new CountDownLatch(1);
        logger.info("Creating the consumer thread");
        Runnable myConsumerRunnable = new ConsumerRunnable("127.0.0.1:9092", "my-fourth-app", "first_topic", latch);
        Thread myConsumerThread = new Thread(myConsumerRunnable);
        myConsumerThread.start();
        try {
            latch.await(); // Don't exit and wait until the thread completes.
        } catch (InterruptedException e) {
            logger.error("Application is interrupted", e);
        } finally {
            logger.info("Application is closing");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Caught shut down hook");
            ((ConsumerRunnable)myConsumerRunnable).shutDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.info("Application is shutting down");
            }
        }));
    }

    public class ConsumerRunnable implements Runnable {

        private CountDownLatch latch;
        private Logger logger = LoggerFactory.getLogger(ConsumerRunnable.class);
        private KafkaConsumer<String, String> consumer;


        public ConsumerRunnable(String bootstrapServers, String groupId, String topic, CountDownLatch latch) {
            this.latch = latch;
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            this.consumer = new KafkaConsumer<String, String>(properties);
            consumer.subscribe(Collections.singleton(topic));
        }

        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        this.logger.info("Key: " + record.key() + ", Value: " + record.value() + ", Partition: " + record.partition());
                    }
                }
            } catch(WakeupException e) {
                this.logger.info("Received shut down signal!");
            } finally {
                consumer.close();
                latch.countDown(); // tell the main code that we're done with the consumer.
            }

        }

        public void shutDown() {
            this.consumer.wakeup(); // method to interrupt consumer.poll. It will throw WakeUpException.
        }
    }
}
