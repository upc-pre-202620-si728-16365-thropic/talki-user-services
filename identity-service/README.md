# identity-service

Autenticación y usuarios de Talki (puerto 8081). Java 17, Spring Boot, JPA/H2 (dev), RabbitMQ.

## Endpoints

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/v1/auth/register` | no | Registra usuario (contraseña: mínimo 8 caracteres con letra y número; segmento `ciclos_1_5` o `ciclos_6_10`) |
| POST | `/v1/auth/login` | no | Devuelve `accessToken` + `refreshToken` |
| POST | `/v1/auth/refresh` | no | Rota el refresh token (US28); devuelve nuevo par |
| POST | `/v1/auth/logout` | opcional | Revoca el refresh token y deniega el access token actual (idempotente) |
| POST | `/v1/auth/verify-email` | no | Consume `{ "token" }` de un solo uso (24 h) → 200 `{"emailVerified": true}`; 400 genérico si inválido/vencido/usado |
| POST | `/v1/auth/forgot-password` | no | Siempre 200 con mensaje único (anti-enumeración); publica el evento solo si el email existe; 429 tras 5 por email / 15 min |
| POST | `/v1/auth/reset-password` | no | `{ "token", "newPassword" }` → 200; PASSWORD_RULE (400 con el mensaje de register); token de un solo uso (30 min); revoca toda la familia de refresh |
| GET | `/v1/users/me` | sí | Datos del usuario autenticado (US23), incluye `emailVerified` |
| POST | `/v1/users/me/resend-verification` | sí | Reemite el token de verificación (invalida el anterior) → 202; 429 tras 3 por email / 15 min |
| PATCH | `/v1/users/me` | sí | `{ "username", "academicSegment" }` opcionales → 200 mismo shape que GET; 400 segmento fuera del catálogo; el `email` del body se ignora (no editable en este slice) |
| POST | `/v1/users/me/voice-consents` | sí | Acepta consentimiento de voz `{ "policyVersion": "v1" }` → 201 (US33); 409 duplicado activo, 400 vacío |
| DELETE | `/v1/users/me/voice-consents/{id}` | sí | Retira el consentimiento (idempotente) → 200 con estado; 404 id ajeno (US33) |
| GET | `/v1/users/me/voice-consents` | sí | Historial del usuario con estado de cada aceptación (US33) |
| GET | `/internal/v1/users/{id}/voice-consent?requiredVersion=v1` | `X-Internal-Api-Key` | Compuerta entre servicios → `{ "active": \|false }`; 401 sin clave, 404 usuario inexistente (US33) |

Errores: registro duplicado → 409; credenciales inválidas → 401 con mensaje genérico único;
contraseña o segmento inválido → 400; límite de intentos excedido → 429 con `Retry-After`;
token de un solo uso inválido/expirado/usado → 400 con mensaje único genérico;
consentimiento duplicado → 409; consentimiento ajeno → 404; policyVersion vacía → 400.

## Consentimiento de voz (US33)

- **Historial**: una fila por aceptación en `voice_consents`; retirar **no elimina** la fila
  (solo marca `withdrawn_at`) — regla Gherkin de US33. Los datos de sesiones no se tocan aquí:
  el borrado/purga distribuida es TS06 (puerta G-PRIV).
- **Compuerta**: tras el retiro, `/internal/v1/users/{id}/voice-consent` responde
  `active=false` y bloquea nuevo procesamiento aunque haya purgas pendientes. Un consentimiento
  `v2` activo cubre un requerimiento `v1`.
- **Autenticación interna**: header `X-Internal-Api-Key` comparado con la env `INTERNAL_API_KEY`
  (obligatoria, sin fallback: si falta, el servicio falla al arrancar).
- **Eventos (PROPUESTA pendiente de ratificación)**: `voice.consent.registered` y
  `voice.consent.withdrawn` sobre `talki.events`, payload sin email ni PII.

## Tokens

- **Access token**: JWT HS256, 15 minutos (configurable con `JWT_ACCESS_TTL_MINUTES`).
  Validado por filtro en todos los endpoints salvo register, login, refresh y actuator/swagger.
- **Refresh token**: valor aleatorio opaco (no JWT). Solo se persiste su hash SHA-256 con
  `userId`, expiración, estado revocado y `replacedByHash` (referencia al token que lo reemplazó).
  Vigencia: `REFRESH_TTL_DAYS` (por defecto 7). Cada refresh lo rota: el usado queda revocado;
  si se reutiliza uno ya rotado se revoca toda la familia activa del usuario.
- **Logout**: agrega el access token a una denylist en memoria (TTL hasta su exp).
- **Tokens de un solo uso (verificación de correo y reset)**: valor aleatorio opaco; en BD solo
  el hash SHA-256 con `expiresAt` y `usedAt`; el crudo viaja únicamente en el evento de
  entrega (`user.email-verification-requested` / `user.password-reset-requested`, PROPUESTA).
  Un solo token activo por usuario: emitir uno nuevo invalida los anteriores (`usedAt=now`).
  Verificación: 24 h. Reset: 30 min; al completarse revoca TODA la familia de refresh tokens
  activos (`revokeAllActiveByUserId`).

> **Importante**: con H2 en memoria un reinicio del servicio borra la tabla `refresh_tokens`,
> por lo que **todos los refresh tokens vigentes quedan invalidados** tras un reinicio.
> Los access tokens en curso también dejan de aceptarse en `/v1/users/me` hasta que el cliente
> vuelva a hacer login (el usuario se re-crea vacío). Migrar a PostgreSQL (perfil prod) elimina
> esta limitación para los refresh tokens.
>
> **Límite conocido tras un reset de contraseña**: los access tokens en curso siguen válidos
> hasta su exp (15 min, comportamiento de la denylist actual); el reset solo revoca la familia
> de refresh tokens.

## Variables de entorno

`PORT`, `JWT_SECRET` (obligatorio, sin fallback), `INTERNAL_API_KEY` (obligatoria, sin fallback,
US33), `JWT_ACCESS_TTL_MINUTES`, `REFRESH_TTL_DAYS`,
`RABBITMQ_HOST/PORT/USERNAME/PASSWORD`. Ver `README-runbook.md` en la raíz del repo.

## Semilla en dev

Con el perfil `dev` activo se siembra la cuenta de demostración del informe AV4
(`alejo.demo.av4@upc.edu.pe`) al arrancar, **nacida verificada** (`emailVerified=true`, no
recibe el flujo de verificación). Fuera del perfil dev no existe ninguna siembra.

## Verificación y recuperación (sprint account-lifecycle)

- `register` crea la cuenta **no verificada** (`emailVerified=false`) y publica
  `user.email-verification-requested` tras guardar.
- `POST /v1/auth/verify-email` verifica el correo con el token; `POST
  /v1/users/me/resend-verification` reemite (3 por email / 15 min, patrón LoginAttemptService).
- `POST /v1/auth/forgot-password` es anti-enumeración: respuesta idéntica exista o no el email.
- `PATCH /v1/users/me` publica `user.profile-updated` (PROPUESTA) con solo username/segmento nuevos.

## No implementado (pendiente)

- Borrado/purga distribuida de datos de voz (TS06): bloqueado por la puerta de privacidad
  G-PRIV (US27, US33, TS06). El slice de retiro de US33 sí está implementado; el retiro solo
  bloquea el procesamiento vía compuerta, no borra datos.
