# README-Runbook — talki-user-services (Sprint 1 closure)

Servicios: **identity-service** (8081) y **notification-service** (8091).
Java 17 · Spring Boot 3.2 · Maven · RabbitMQ · JPA/H2.

## 1. Variables de entorno obligatorias

Copia `.env.example` a `.env` (docker compose lo carga automáticamente):

| Variable | Servicio | Default | Notas |
|----------|----------|---------|-------|
| `PORT` | identity / notification | 8081 / 8091 | puerto HTTP |
| `JWT_SECRET` | identity | **sin fallback** | si falta, **el servicio falla al arrancar** (SB1-07-D) |
| `INTERNAL_API_KEY` | identity | **sin fallback** | clave de la compuerta interna `/internal/**` (US33); si falta, **falla al arrancar** |
| `JWT_ACCESS_TTL_MINUTES` | identity | `15` | vigencia del access token |
| `REFRESH_TTL_DAYS` | identity | `7` | vigencia del refresh token |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | ambos | localhost/5672/guest/guest | broker |

En `docker-compose.dev.yml` el identity usa `RABBITMQ_HOST=rabbitmq` y exige `JWT_SECRET`.

## 2. Arranque

```bash
# Local (sin Docker): el secret es obligatorio
export JWT_SECRET=$(openssl rand -base64 48)
mvn -B -ntp clean test && mvn spring-boot:run   # en identity-service/ y notification-service/

# Compose de desarrollo (perfil dev, consola H2 y siembra solo aquí)
cp .env.example .env   # editar JWT_SECRET
docker compose -f docker-compose.dev.yml up --build
```

**Nota H2**: con H2 en memoria, un reinicio de identity-service invalida todos los refresh
tokens (y borra usuarios sembrados salvo la cuenta demo de dev). Ver `identity-service/README.md`.

## 3. Contrato de los endpoints de auth (para consumos de Kevin y Eduardo)

### POST /v1/auth/register → 201
Body: `{ "email", "password", "username", "academicSegment" }`.
Contraseña: mínimo 8 caracteres, al menos una letra y un número. Segmento: `ciclos_1_5` o `ciclos_6_10`.
Errores: 409 duplicado · 400 contraseña/segmento inválido.

### POST /v1/auth/login → 200
```json
{ "accessToken": "<jwt>", "refreshToken": "<opaco>", "tokenType": "Bearer" }
```
Errores: **401** `{"message": "Credenciales inválidas"}` (mensaje único, no revela email/contraseña).
**429** con header `Retry-After` y `retryAfterSeconds` tras 5 fallos en 15 minutos por email.

### POST /v1/auth/refresh → 200
Body: `{ "refreshToken": "<opaco>" }`. Respuesta: nuevo par `accessToken` + `refreshToken`
(el anterior queda revocado — rotación). Errores **401** si el token es inexistente, vencido,
revocado o ya rotado; si se reutiliza uno ya rotado se revoca **toda la familia activa** del usuario.

### POST /v1/auth/logout → 204 (idempotente)
Body: `{ "refreshToken": "<opaco>" }` + header `Authorization: Bearer <accessToken>`.
Revoca el refresh y añade el access token a la denylist (queda 401 en endpoints protegidos).

### GET /v1/users/me → 200 (requiere `Authorization: Bearer <accessToken>`)
Devuelve `userId`, `email`, `username`, `academicSegment`, `role`. **401** sin token, con token
vencido, con firma inválida o denylist (logout).

### Consentimiento de voz (US33) — slice Sprint 1: RETIRO

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/v1/users/me/voice-consents` | JWT | Registra `{ "policyVersion": "v1" }` → **201** `{consentId, policyVersion, scope, acceptedAt}`. **409** si ya hay una ACTIVA con la misma versión · **400** policyVersion vacío |
| DELETE | `/v1/users/me/voice-consents/{id}` | JWT | Registra el retiro → **200** con el estado (`withdrawnAt`). Idempotente · **404** si el id no pertenece al usuario |
| GET | `/v1/users/me/voice-consents` | JWT | Historial del usuario: una fila por aceptación con su estado |
| GET | `/internal/v1/users/{id}/voice-consent?requiredVersion=v1` | `X-Internal-Api-Key` | Compuerta para Sessions/LiveCoach → `{ "active": true\|false }` · **401** sin/mal clave · **404** usuario inexistente |

**Regla del historial**: retirar consentimiento NO elimina la fila (solo marca `withdrawnAt`);
los datos de sesiones no se tocan aquí — el borrado/purga distribuida es TS06 (contexto
Sharing & Retention, puerta G-PRIV).

**Contrato de la compuerta (para Kevin/Eduardo)**: tras el retiro, `GET /internal/v1/users/{id}/voice-consent`
responde `active=false` y **bloquea nuevo procesamiento aunque haya purgas pendientes**.
Un consentimiento `v2` activo cubre un requerimiento `v1`; `requiredVersion` mayor a la
aceptada responde `false`.

## 4. Verificación con curl (tokens enmascarados)

```bash
# Login
curl -s -X POST http://localhost:8081/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"manuel@upc.edu.pe","password":"Password123"}'
# → {"accessToken":"eyJhbGciOi…<mask>","refreshToken":"tQj2xR…<mask>","tokenType":"Bearer"}

# Endpoint protegido
curl -s http://localhost:8081/v1/users/me -H "Authorization: Bearer eyJhbGciOi…<mask>"
# → {"userId":1,"email":"manuel@upc.edu.pe","username":"ManuelTumi",…}

# US33: aceptar consentimiento de voz
curl -s -X POST http://localhost:8081/v1/users/me/voice-consents \
  -H "Authorization: Bearer eyJhbGciOi…<mask>" -H "Content-Type: application/json" \
  -d '{"policyVersion":"v1"}'
# → 201 {"consentId":"3f2c9a…","policyVersion":"v1","scope":"voice_processing","acceptedAt":"2026-…"}

# US33: retirar consentimiento (idempotente; la fila permanece en el historial)
curl -s -X DELETE http://localhost:8081/v1/users/me/voice-consents/3f2c9a…<mask> \
  -H "Authorization: Bearer eyJhbGciOi…<mask>"
# → 200 {"consentId":"3f2c9a…","withdrawnAt":"2026-…"}

# US33: compuerta interna (Sessions/LiveCoach) — sin JWT, con X-Internal-Api-Key
curl -s "http://localhost:8081/internal/v1/users/1/voice-consent?requiredVersion=v1" \
  -H "X-Internal-Api-Key: <INTERNAL_API_KEY>"
# → {"active":false}  (true antes del retiro)

# Refresh (rota: el refreshToken anterior muere)
curl -s -X POST http://localhost:8081/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"tQj2xR…<mask>"}'

# Logout (idempotente)
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8081/v1/auth/logout \
  -H "Authorization: Bearer eyJhbGciOi…<mask>" -H "Content-Type: application/json" \
  -d '{"refreshToken":"tQj2xR…<mask>"}'
# → 204; el access token anterior pasa a 401 y el refresh ya no renueva

# Credenciales inválidas → mensaje único genérico
curl -s -X POST http://localhost:8081/v1/auth/login \
  -H "Content-Type: application/json" -d '{"email":"x@y.com","password":"Wrong1234"}'
# → {"status":401,"error":"Unauthorized","message":"Credenciales inválidas"}
```

## 5. 🚪 Puerta de privacidad G-PRIV: implementado el retiro (US33), pendiente la purga (TS06)

El **retiro de consentimiento de voz (US33)** ya está implementado y es la compuerta que
detiene el procesamiento: tras el retiro, `/internal/v1/users/{id}/voice-consent` responde
`active=false`. Nada de este repo procesa datos personales más allá de las fixtures de
desarrollo hasta que la puerta **G-PRIV** esté cerrada. Sigue bloqueado:

- **Borrado/purga distribuida de datos de voz** (TS06, contexto Sharing & Retention):
  pendiente de G-PRIV. Retirar el consentimiento **no elimina** el historial de
  consentimientos ni los datos de sesiones — solo bloquea el procesamiento vía compuerta.
- `deletion.completed` (consumer en notification-service): pendiente de G-PRIV.
- Verificación de correo, recuperación de contraseña y PATCH `/v1/users/me`: no implementados.

## 6. No implementado (documentado)

- Verificación de correo electrónico.
- Recuperación de contraseña.
- PATCH `/v1/users/me`.
- Borrado/purga distribuida de datos de voz (TS06) y flujos ligados a G-PRIV;
  el slice de retiro de US33 sí está implementado (ver §5).

## 7. Eventos de consentimiento (PROPUESTA — pendiente de ratificación)

El informe aún no fija el contrato técnico; estas routing keys y payloads son propuesta
sobre el exchange `talki.events`:

| Routing key | Publica | Payload (ejemplo) |
|-------------|---------|-------------------|
| `voice.consent.registered` | identity | `{"eventId":"<uuid>","eventType":"voice.consent.registered","occurredAt":"2026-…T…Z","userId":1,"policyVersion":"v1","scope":"voice_processing","consentId":"<uuid>"}` |
| `voice.consent.withdrawn` | identity → notification | ídem con `eventType` de retiro; dispara el aviso al usuario y la cola `notification.voice-consent-withdrawn` (DLQ propia, dedup por `eventId`) |
