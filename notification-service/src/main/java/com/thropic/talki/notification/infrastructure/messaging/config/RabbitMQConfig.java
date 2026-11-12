package com.thropic.talki.notification.infrastructure.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE        = "talki.events";
    public static final String DLX             = "talki.dlx";

    public static final String QUEUE_SCORE     = "notification.score-ready";
    public static final String ROUTING_SCORE   = "scoring.completed";

    public static final String QUEUE_ACHIEVE   = "notification.achievement-ready";
    public static final String ROUTING_ACHIEVE = "achievement.unlocked";

    public static final String QUEUE_SCORE_DLQ     = QUEUE_SCORE + ".dlq";
    public static final String QUEUE_ACHIEVE_DLQ   = QUEUE_ACHIEVE + ".dlq";

    @Bean
    public TopicExchange talkiEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue scoreQueue() {
        return QueueBuilder.durable(QUEUE_SCORE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_SCORE_DLQ)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    @Bean
    public Queue achievementQueue() {
        return QueueBuilder.durable(QUEUE_ACHIEVE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_ACHIEVE_DLQ)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    /** Colas de muertos: reciben lo que el consumidor rechaza (SB1-05-B). */
    @Bean
    public Queue scoreDlq() {
        return QueueBuilder.durable(QUEUE_SCORE_DLQ).build();
    }

    @Bean
    public Queue achievementDlq() {
        return QueueBuilder.durable(QUEUE_ACHIEVE_DLQ).build();
    }

    @Bean
    public Binding scoreBinding() {
        return BindingBuilder.bind(scoreQueue()).to(talkiEventsExchange()).with(ROUTING_SCORE);
    }

    @Bean
    public Binding achievementBinding() {
        return BindingBuilder.bind(achievementQueue()).to(talkiEventsExchange()).with(ROUTING_ACHIEVE);
    }

    @Bean
    public Binding scoreDlqBinding() {
        return BindingBuilder.bind(scoreDlq()).to(deadLetterExchange()).with(QUEUE_SCORE_DLQ);
    }

    @Bean
    public Binding achievementDlqBinding() {
        return BindingBuilder.bind(achievementDlq()).to(deadLetterExchange()).with(QUEUE_ACHIEVE_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // Sin requeue: un mensaje inválido se rechaza y el broker lo manda a la DLQ
        // (evita loops infinitos de poison messages).
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
