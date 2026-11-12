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

    // Colas del ciclo de vida de la cuenta (routing keys PROPUESTAS pendientes de ratificación).
    public static final String QUEUE_EMAIL_VERIFICATION   = "notification.email-verification-requested";
    public static final String ROUTING_EMAIL_VERIFICATION = "user.email-verification-requested";
    public static final String QUEUE_PASSWORD_RESET       = "notification.password-reset-requested";
    public static final String ROUTING_PASSWORD_RESET     = "user.password-reset-requested";
    public static final String QUEUE_PROFILE_UPDATED      = "notification.profile-updated";
    public static final String ROUTING_PROFILE_UPDATED    = "user.profile-updated";

    public static final String QUEUE_SCORE_DLQ     = QUEUE_SCORE + ".dlq";
    public static final String QUEUE_ACHIEVE_DLQ   = QUEUE_ACHIEVE + ".dlq";
    public static final String QUEUE_USER_REG_DLQ  = QUEUE_USER_REG + ".dlq";
    public static final String QUEUE_VOICE_CONSENT_WITHDRAWN_DLQ = QUEUE_VOICE_CONSENT_WITHDRAWN + ".dlq";
    public static final String QUEUE_EMAIL_VERIFICATION_DLQ = QUEUE_EMAIL_VERIFICATION + ".dlq";
    public static final String QUEUE_PASSWORD_RESET_DLQ     = QUEUE_PASSWORD_RESET + ".dlq";
    public static final String QUEUE_PROFILE_UPDATED_DLQ    = QUEUE_PROFILE_UPDATED + ".dlq";

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

    @Bean
    public Queue emailVerificationQueue() {
        return QueueBuilder.durable(QUEUE_EMAIL_VERIFICATION)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EMAIL_VERIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue passwordResetQueue() {
        return QueueBuilder.durable(QUEUE_PASSWORD_RESET)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PASSWORD_RESET_DLQ)
                .build();
    }

    @Bean
    public Queue profileUpdatedQueue() {
        return QueueBuilder.durable(QUEUE_PROFILE_UPDATED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PROFILE_UPDATED_DLQ)
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
    public Queue emailVerificationDlq() {
        return QueueBuilder.durable(QUEUE_EMAIL_VERIFICATION_DLQ).build();
    }

    @Bean
    public Queue passwordResetDlq() {
        return QueueBuilder.durable(QUEUE_PASSWORD_RESET_DLQ).build();
    }

    @Bean
    public Queue profileUpdatedDlq() {
        return QueueBuilder.durable(QUEUE_PROFILE_UPDATED_DLQ).build();
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
    public Binding emailVerificationBinding() {
        return BindingBuilder.bind(emailVerificationQueue()).to(talkiEventsExchange()).with(ROUTING_EMAIL_VERIFICATION);
    }

    @Bean
    public Binding passwordResetBinding() {
        return BindingBuilder.bind(passwordResetQueue()).to(talkiEventsExchange()).with(ROUTING_PASSWORD_RESET);
    }

    @Bean
    public Binding profileUpdatedBinding() {
        return BindingBuilder.bind(profileUpdatedQueue()).to(talkiEventsExchange()).with(ROUTING_PROFILE_UPDATED);
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
    public Binding emailVerificationDlqBinding() {
        return BindingBuilder.bind(emailVerificationDlq()).to(deadLetterExchange()).with(QUEUE_EMAIL_VERIFICATION_DLQ);
    }

    @Bean
    public Binding passwordResetDlqBinding() {
        return BindingBuilder.bind(passwordResetDlq()).to(deadLetterExchange()).with(QUEUE_PASSWORD_RESET_DLQ);
    }

    @Bean
    public Binding profileUpdatedDlqBinding() {
        return BindingBuilder.bind(profileUpdatedDlq()).to(deadLetterExchange()).with(QUEUE_PROFILE_UPDATED_DLQ);
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
