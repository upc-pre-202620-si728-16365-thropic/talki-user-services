package com.thropic.talki.notification.infrastructure.messaging.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void mainQueues_declareDeadLetteringTowardTheirDlq() {
        Queue score = config.scoreQueue();
        Queue achievement = config.achievementQueue();

        assertThat(score.getName()).isEqualTo(RabbitMQConfig.QUEUE_SCORE);
        assertThat(score.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.QUEUE_SCORE_DLQ);
        assertThat(achievement.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DLX)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.QUEUE_ACHIEVE_DLQ);
    }

    @Test
    void dlqQueues_areDeclaredAndBoundToDlx() {
        Queue scoreDlq = config.scoreDlq();
        Queue achievementDlq = config.achievementDlq();
        Binding scoreBinding = config.scoreDlqBinding();
        Binding achievementBinding = config.achievementDlqBinding();

        assertThat(scoreDlq.getName()).isEqualTo(RabbitMQConfig.QUEUE_SCORE_DLQ);
        assertThat(achievementDlq.getName()).isEqualTo(RabbitMQConfig.QUEUE_ACHIEVE_DLQ);
        assertThat(scoreBinding.getExchange()).isEqualTo(RabbitMQConfig.DLX);
        assertThat(scoreBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.QUEUE_SCORE_DLQ);
        assertThat(achievementBinding.getExchange()).isEqualTo(RabbitMQConfig.DLX);
        assertThat(achievementBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.QUEUE_ACHIEVE_DLQ);
    }

    @Test
    void listenerFactory_rejectsInsteadOfRequeueing() {
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(mock(ConnectionFactory.class));

        // Sin requeue: mensaje inválido → reject → broker lo enruta a la DLQ.
        // (el getter es protected en spring-amqp 3.x; se lee el campo del factory)
        Boolean rejected = (Boolean) org.springframework.test.util.ReflectionTestUtils
                .getField(factory, "defaultRequeueRejected");
        assertThat(rejected).isFalse();
    }

    @Test
    void invalidMessagePayload_failsConversion_andIsRejectedToDlq() {
        Jackson2JsonMessageConverter converter = config.messageConverter();

        // Un payload corrupto (poison message) no se puede convertir; con
        // defaultRequeueRejected=false el contenedor lo rechaza → DLQ.
        org.springframework.amqp.core.Message invalid =
                new org.springframework.amqp.core.Message(
                        "{no-es-json".getBytes(),
                        new org.springframework.amqp.core.MessageProperties());
        assertThatThrownBy(() -> converter.fromMessage(invalid))
                .isInstanceOf(MessageConversionException.class);
    }
}
