package com.thropic.talki.identity.infrastructure.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thropic.talki.identity.domain.event.VoiceConsentEvent;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de mensajería de identity-service como productor de eventos
 * sobre el exchange topic {@code talki.events}. Coherente con la configuración
 * homónima de los demás microservicios (live-coach, filler, scoring, ...).
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE             = "talki.events";
    public static final String USER_REGISTERED_KEY  = "user.registered";

    // Routing keys PROPUESTAS de US33 (pendientes de ratificación en el informe).
    public static final String VOICE_CONSENT_REGISTERED_KEY = VoiceConsentEvent.TYPE_REGISTERED;
    public static final String VOICE_CONSENT_WITHDRAWN_KEY  = VoiceConsentEvent.TYPE_WITHDRAWN;

    @Bean
    public TopicExchange talkiEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
