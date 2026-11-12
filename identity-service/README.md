# identity-service

Autenticación y usuarios de Talki (puerto 8081). Java 17, Spring Boot, JPA/H2 (dev), RabbitMQ.

## Endpoints

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/v1/auth/register` | no | Registra usuario (contraseña: mínimo 8 caracteres con letra y número; segmento `ciclos_1_5` o `ciclos_6_10`) |
| POST | `/v1/auth/login` | no | Devuelve `accessToken` + `refreshToken` |
| POST | `/v1/auth/refresh` | no | Rota el refresh token (US28); devuelve nuevo par |
| POST | `/v1/auth/logout` | opcional | Revoca el refresh token y deniega el access token actual (idempotente) |
| GET | `/v1/users/me` | sí | Datos del usuario autenticado (US23) |
| POST | `/v1/users/me/voice-consents` | sí | Acepta consentimiento de voz `{ "policyVersion": "v1" }` → 201 (US33); 409 duplicado activo, 400 vacío |
| DELETE | `/v1/users/me/voice-consents/{id}` | sí | Retira el consentimiento (idempotente) → 200 con estado; 404 id ajeno (US33) |
| GET | `/v1/users/me/voice-consents` | sí | Historial del usuario con estado de cada aceptación (US33) |
| GET | `/internal/v1/users/{id}/voice-consent?requiredVersion=v1` | `X-Internal-Api-Key` | Compuerta entre servicios → `{ "active": \|false }`; 401 sin clave, 404 usuario inexistente (US33) |

Errores: registro duplicado → 409; credenciales inválidas → 401 con mensaje genérico único;
contraseña o segmento inválido → 400; límite de intentos excedido → 429 con `Retry-After`;
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

> **Importante**: con H2 en memoria un reinicio del servicio borra la tabla `refresh_tokens`,
> por lo que **todos los refresh tokens vigentes quedan invalidados** tras un reinicio.
> Los access tokens en curso también dejan de aceptarse en `/v1/users/me` hasta que el cliente
> vuelva a hacer login (el usuario se re-crea vacío). Migrar a PostgreSQL (perfil prod) elimina
> esta limitación para los refresh tokens.

## Variables de entorno

`PORT`, `JWT_SECRET` (obligatorio, sin fallback), `INTERNAL_API_KEY` (obligatoria, sin fallback,
US33), `JWT_ACCESS_TTL_MINUTES`, `REFRESH_TTL_DAYS`,
`RABBITMQ_HOST/PORT/USERNAME/PASSWORD`. Ver `README-runbook.md` en la raíz del repo.

## Semilla en dev

Con el perfil `dev` activo se siembra la cuenta de demostración del informe AV4
(`alejo.demo.av4@upc.edu.pe`) al arrancar. Fuera del perfil dev no existe ninguna siembra.

## No implementado (pendiente)

- Verificación de correo electrónico.
- Recuperación de contraseña.
- PATCH `/v1/users/me` (edición de perfil).
- Borrado/purga distribuida de datos de voz (TS06): bloqueado por la puerta de privacidad
  G-PRIV (US27, US33, TS06). El slice de retiro de US33 sí está implementado; el retiro solo
  bloquea el procesamiento vía compuerta, no borra datos.
