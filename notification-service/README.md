# notification-service

Consumidor de eventos de Talki (puerto 8091). Java 17, Spring Boot, RabbitMQ.

## Colas

| Cola | Routing key | Evento |
|------|-------------|--------|
| `notification.score-ready` | `scoring.completed` | Puntaje de sesión |
| `notification.achievement-ready` | `achievement.unlocked` | Logro desbloqueado |
| `notification.user-registered` | `user.registered` | Usuario registrado |
| `notification.voice-consent-withdrawn` | `voice.consent.withdrawn` | Retiro de consentimiento de voz (US33, **routing key PROPUESTA** pendiente de ratificación) |
| `notification.email-verification-requested` | `user.email-verification-requested` | Verificación de correo (ciclo de vida, **routing key PROPUESTA** pendiente de ratificación) |
| `notification.password-reset-requested` | `user.password-reset-requested` | Recuperación de contraseña (ciclo de vida, **routing key PROPUESTA** pendiente de ratificación) |
| `notification.profile-updated` | `user.profile-updated` | Perfil actualizado (ciclo de vida, **routing key PROPUESTA** pendiente de ratificación) |
| `notification.score-ready.dlq` | (DLX) | Muertos de score-ready |
| `notification.achievement-ready.dlq` | (DLX) | Muertos de achievement-ready |
| `notification.user-registered.dlq` | (DLX) | Muertos de user-registered |
| `notification.voice-consent-withdrawn.dlq` | (DLX) | Muertos de voice-consent-withdrawn |
| `notification.email-verification-requested.dlq` | (DLX) | Muertos de email-verification-requested |
| `notification.password-reset-requested.dlq` | (DLX) | Muertos de password-reset-requested |
| `notification.profile-updated.dlq` | (DLX) | Muertos de profile-updated |

- Exchange: `talki.events` (topic); DLX: `talki.dlx` (direct).
- `defaultRequeueRejected=false`: un mensaje inválido se rechaza y el broker lo envía a su DLQ.
- Deduplicación por `eventId` en memoria (ventana TTL de 10 minutos): un redelivery del mismo
  evento no genera un segundo aviso.
- Los consumers de verificación y recuperación entregan el token crudo al usuario por el canal
  (hoy, log); el email del evento solo se usa como destino de entrega conceptual
  (database-per-service), nunca se loguea.

## Canal de entrega

**El canal real es el log de la aplicación** (`LogNotificationChannel`, único adaptador del puerto
`NotificationChannel`). **WebSocket y correo son propuestos, no configurados** en el Sprint 1.

## Pendientes

- **`deletion.completed`**: pendiente de la puerta de privacidad **G-PRIV** (US27, US33, TS06).
  No hay consumer ni cola para ese evento todavía; la purga de datos de voz sigue pendiente
  (el retiro de US33 solo bloquea el procesamiento vía compuerta de identity-service).
- Canal WebSocket y canal de correo: propuestos, no configurados.
