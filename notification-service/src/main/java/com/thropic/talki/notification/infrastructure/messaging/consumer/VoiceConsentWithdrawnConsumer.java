package com.thropic.talki.notification.infrastructure.messaging.consumer;

import com.thropic.talki.notification.application.port.NotificationChannel;
import com.thropic.talki.notification.domain.event.VoiceConsentWithdrawnEvent;
import com.thropic.talki.notification.infrastructure.messaging.EventDeduplicator;
import com.thropic.talki.notification.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer mínimo de voice.consent.withdrawn (US33): informa al usuario que
 * su consentimiento de voz fue revocado y el procesamiento quedó bloqueado.
 * Deduplicación por eventId (redelivery del broker → un solo aviso).
 */
@Component
public class VoiceConsentWithdrawnConsumer {

    private static final Logger log = LoggerFactory.getLogger(VoiceConsentWithdrawnConsumer.class);

    static final String TITLE = "Consentimiento de voz revocado";
    static final String BODY = "Consentimiento de voz revocado — el procesamiento quedó bloqueado";

    private final EventDeduplicator deduplicator;
    private final NotificationChannel notificationChannel;

    public VoiceConsentWithdrawnConsumer(EventDeduplicator deduplicator,
                                         NotificationChannel notificationChannel) {
        this.deduplicator = deduplicator;
        this.notificationChannel = notificationChannel;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_VOICE_CONSENT_WITHDRAWN)
    public void onVoiceConsentWithdrawn(VoiceConsentWithdrawnEvent event) {
        if (deduplicator.isDuplicate(event.getEventId())) {
            log.info("[notification-service] voice.consent.withdrawn duplicado, se ignora — eventId={}",
                    event.getEventId());
            return;
        }

        log.info("[notification-service] Consentimiento de voz retirado — userId={} consentId={}",
                event.getUserId(), event.getConsentId());

        notificationChannel.send(String.valueOf(event.getUserId()), TITLE, BODY);
    }
}
