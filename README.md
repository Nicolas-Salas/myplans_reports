# myplans_reports — Reports Service

Quinto microservicio de MyPlans, encargado de generar entregables Excel del plano (matriz de TAGs + historial de cambios). Cubre **RF-21**, **RF-25** y **CU-18**.

## Tabla de contenido
- [Stack](#stack)
- [Cómo correrlo](#cómo-correrlo)
- [Endpoint](#endpoint)
- [Arquitectura del flujo](#arquitectura-del-flujo)
- [Variables de configuración](#variables-de-configuración)
- [Troubleshooting](#troubleshooting)

---

## Stack

- **Java 17 + Spring Boot 3.2.6** (Web, Security, Actuator, Validation)
- **Apache POI 5.2.5** para generar `.xlsx` con 2 sheets, estilos, bordes y autosize
- **JJWT 0.12.5** para validar JWT (mismo secret y algoritmo HS384 que los demás servicios)
- **springdoc 2.3.0** para OpenAPI/Swagger
- **NO usa JPA ni MySQL** — el Reports Service es stateless, todos los datos vienen vía REST del Core y del Audit
- **Lombok** para reducir boilerplate

## Cómo correrlo

```bash
cd reports
mvn clean package -DskipTests
java -jar target/reports-1.0.0.jar
```

Puerto por defecto: `8083`.

Dependencias en runtime:
- Core Service en `http://localhost:8081`
- Audit Service en `http://localhost:8082`

Ambos deben aceptar el mismo `X-Internal-Token` que tiene este servicio. **En producción cambia el token** vía variables de entorno `CORE_INTERNAL_TOKEN` y `AUDIT_INTERNAL_TOKEN`.

## Endpoint

```
GET /api/v1/reportes/plano/{idPlano}/excel
```

| Header | Valor |
|--------|-------|
| `Authorization` | `Bearer <JWT_con_rol_SUPERVISOR_o_ADMIN>` |

Respuestas:

| Código | Significado |
|--------|-------------|
| 200    | Excel generado (Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) |
| 400    | El plano no está en estado CERRADO (RF-25) |
| 401    | Sin token, token expirado, o token inválido |
| 403    | El usuario no tiene rol SUPERVISOR o ADMIN |
| 404    | El plano no existe |
| 502    | El Core o el Audit no respondieron |

## Arquitectura del flujo

```
                       ┌─────────────────┐
       Supervisor      │  API Gateway    │  /api/v1/reportes/plano/{id}/excel
            ──────────▶│   (port 8095)   │────────┐
                       └─────────────────┘        │
                                                  ▼
                                          ┌─────────────────┐
                                          │  Reports Service│  (port 8083)
                                          │                 │
                                          │  1. valida JWT  │
                                          │  2. pide plano  │
                                          │     + tags al   │
                                          │     Core        │
                                          │  3. pide hist.  │
                                          │     al Audit    │
                                          │  4. genera xlsx │
                                          └────────┬────────┘
                                                   │
                                                   │ X-Internal-Token
                                  ┌────────────────┴────────────────┐
                                  ▼                                 ▼
                          ┌───────────────┐                 ┌────────────────┐
                          │ Core Service  │                 │ Audit Service  │
                          │  (port 8081)  │                 │  (port 8082)   │
                          │ GET /planos/  │                 │ GET /historial │
                          │ GET /tags/    │                 │      /tag/{id} │
                          └───────────────┘                 └────────────────┘
```

El Reports Service **no se autentica con JWT contra el Core/Audit** porque no es un usuario; usa el patrón **service-to-service** con header `X-Internal-Token`. Tanto el Core como el Audit aceptan este header y autentican al request como un principal interno con roles suficientes (`ROLE_REPORTS_SERVICE` + `ROLE_ADMIN` en ambos casos).

## Variables de configuración

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SERVER_PORT` | `8083` | Puerto HTTP |
| `JWT_SECRET` | (dev secret) | Secret HS384 compartido entre todos los servicios |
| `CORE_SERVICE_URI` | `http://localhost:8081` | URL del Core |
| `CORE_INTERNAL_TOKEN` | (dev token) | Token interno compartido con el Core |
| `CORE_TIMEOUT_MS` | `5000` | Timeout (ms) para llamadas al Core |
| `AUDIT_SERVICE_URI` | `http://localhost:8082` | URL del Audit |
| `AUDIT_INTERNAL_TOKEN` | (dev token) | Token interno compartido con el Audit |
| `AUDIT_TIMEOUT_MS` | `5000` | Timeout (ms) para llamadas al Audit |

## Estructura del Excel generado

El archivo `.xlsx` tiene 2 hojas:

### Hoja 1: "Matriz TAGs"
- Filas 1-2: metadatos del plano (nombre, código, rev, formulario, status, responsable)
- Fila 3: vacía (separador visual)
- Fila 4: headers (ID TAG, Código, Tipo, Descripción, Área, Estado actual, Comentario, Usuario ingreso, Fecha ingreso, Usuario última act., Última modificación)
- Fila 5 en adelante: una fila por cada TAG del plano

### Hoja 2: "Historial"
- Fila 1: headers (ID TAG, Código TAG, Fecha cambio, ID Usuario, Estado anterior, Estado nuevo, Observaciones)
- Fila 2 en adelante: cada cambio de estado registrado por el Audit, agrupado por TAG

Si no hay historial (TAGs sin cambios), aparece un mensaje "(No hay registros de auditoría para este plano)".

## Troubleshooting

### "Problems: 56" en VS Code / IntelliJ después del primer `mvn install`
Es Lombok que necesita que el IDE le recargue el classpath. Solución:
- **VS Code**: `Ctrl/Cmd+Shift+P` → "Java: Clean Java Language Server Workspace" y reinicia.
- **IntelliJ**: `File` → `Invalidate Caches and Restart`.
- Verifica que tengas el **plugin Lombok** instalado.

### CORS (al consumir desde frontend)
El Reports Service **no** habilita CORS. **Toda la entrada al sistema desde el frontend debe pasar por el Gateway** (puerto 8095), que sí configura CORS. Si intentas pegarle directamente al Reports Service desde el browser, te dará error de CORS — eso es por diseño.

### 502 al exportar
Algún servicio downstream no respondió. Revisa:
```bash
curl http://localhost:8081/actuator/health   # Core
curl http://localhost:8082/actuator/health   # Audit
```
Y el log del Reports Service:
```
ERROR ... Upstream service error: Audit respondió 403 FORBIDDEN al consultar TAG X
```
Si ves 403, significa que el Audit no tiene tu rol o el internal-token no coincide. Verifica que `AUDIT_INTERNAL_TOKEN` del Reports sea igual al `audit.internal.token` del Audit.

### El sheet "Historial" sale vacío
Significa que el Audit no tiene registros para los TAGs del plano. Esto pasa cuando:
- El Core no está publicando eventos al Audit (revisa que `TagServiceImpl.updateEstado()` llame a `auditServiceClient.publish(event)`).
- El Audit se reinició después de que el Core publicó los eventos (su BD H2 in-memory se perdió).
- Los tokens internos no coinciden y el Core está fallando silenciosamente (modo `audit.enforce-strict=false`).

Para verificar que el Audit sí tiene los datos:
```bash
curl http://localhost:8082/api/v1/historial/tag/1 \
  -H "X-Internal-Token: <token>"
```

### El plano se queda con `status=ABIERTO` después de "cerrar"
Para cerrar el plano necesitas estar autenticado como `SUPERVISOR` o `ADMIN`. Verifica los roles del JWT.

## Roadmap (fuera de scope para Sprint 1)

- Cache de planos cerrados (no cambian, se pueden cachear los Excel generados)
- Generación asíncrona con cola RabbitMQ + endpoint de polling (`GET /reportes/{jobId}/status`)
- Plantillas personalizadas por cliente
- Exportar también a PDF
- Notificación por email del Excel listo
