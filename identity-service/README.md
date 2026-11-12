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

Errores: registro duplicado → 409; credenciales inválidas → 401 con mensaje genérico único;
contraseña o segmento inválido → 400; límite de intentos excedido → 429 con `Retry-After`.

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

`PORT`, `JWT_SECRET` (obligatorio, sin fallback), `JWT_ACCESS_TTL_MINUTES`, `REFRESH_TTL_DAYS`,
`RABBITMQ_HOST/PORT/USERNAME/PASSWORD`. Ver `README-runbook.md` en la raíz del repo.

## Semilla en dev

Con el perfil `dev` activo se siembra la cuenta de demostración del informe AV4
(`alejo.demo.av4@upc.edu.pe`) al arrancar. Fuera del perfil dev no existe ninguna siembra.

## No implementado (pendiente)

- Verificación de correo electrónico.
- Recuperación de contraseña.
- PATCH `/v1/users/me` (edición de perfil).
- Consentimientos de voz (US33): bloqueado por la puerta de privacidad G-PRIV (US27, US33, TS06).
