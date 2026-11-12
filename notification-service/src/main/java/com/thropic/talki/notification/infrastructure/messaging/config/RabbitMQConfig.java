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

    public static final String QUEUE_USER_REG   = "notification.user-registered";
    public static final String ROUTING_USER_REG = "user.registered";

    public static final String QUEUE_VOICE_CONSENT_WITHDRAWN   = "notification.voice-consent-withdrawn";
    public static final String ROUTING_VOICE_CONSENT_WITHDRAWN = "voice.consent.withdrawn"; // US33, PROPUESTA

    public static final String QUEUE_SCORE_DLQ     = QUEUE_SCORE + ".dlq";
    public static final String QUEUE_ACHIEVE_DLQ   = QUEUE_ACHIEVE + ".dlq";
    public static final String QUEUE_USER_REG_DLQ  = QUEUE_USER_REG + ".dlq";
    public static final String QUEUE_VOICE_CONSENT_WITHDRAWN_DLQ = QUEUE_VOICE_CONSENT_WITHDRAWN + ".dlq";

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

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_USER_REG)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_USER_REG_DLQ)
                .build();
    }

    @Bean
    public Queue voiceConsentWithdrawnQueue() {
        return QueueBuilder.durable(QUEUE_VOICE_CONSENT_WITHDRAWN)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_VOICE_CONSENT_WITHDRAWN_DLQ)
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
    public Queue userRegisteredDlq() {
        return QueueBuilder.durable(QUEUE_USER_REG_DLQ).build();
    }

    @Bean
    public Queue voiceConsentWithdrawnDlq() {
        return QueueBuilder.durable(QUEUE_VOICE_CONSENT_WITHDRAWN_DLQ).build();
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
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue()).to(talkiEventsExchange()).with(ROUTING_USER_REG);
    }

    @Bean
    public Binding voiceConsentWithdrawnBinding() {
        return BindingBuilder.bind(voiceConsentWithdrawnQueue()).to(talkiEventsExchange()).with(ROUTING_VOICE_CONSENT_WITHDRAWN);
    }

    @Bean
    public Binding userRegisteredDlqBinding() {
        return BindingBuilder.bind(userRegisteredDlq()).to(deadLetterExchange()).with(QUEUE_USER_REG_DLQ);
    }

    @Bean
    public Binding voiceConsentWithdrawnDlqBinding() {
        return BindingBuilder.bind(voiceConsentWithdrawnDlq()).to(deadLetterExchange()).with(QUEUE_VOICE_CONSENT_WITHDRAWN_DLQ);
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
