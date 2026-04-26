# Instrucciones para GitHub Copilot — Backend Core (Spring Boot)
## Configurable Workflow System · SW1 2026

> Este repositorio contiene el **backend principal** del sistema.
> Para el modelo de datos completo ver `../.github/DATABASE.md`.
> Para la arquitectura detallada ver `../.github/SPRINGBOOT.md`.

---

## Stack

- Java 21 / Spring Boot 4.x
- Spring Security + JWT (jjwt 0.12.3)
- Spring Data MongoDB (`@Document`, `MongoRepository`)
- Spring WebSocket (STOMP, endpoint `/ws`)
- Spring WebFlux / WebClient (para llamadas a FastAPI)
- Lombok

## Paquete Base

```
sw1.p1
```

## Arquitectura: Package-by-Feature

Cada feature tiene sus propias capas internas:

```
feature/
├── api/           ← @RestController
├── application/   ← @Service
├── domain/        ← @Document + Repository
└── dto/           ← Request / Response
```

Cross-cutting en raíz: `config/`, `security/`, `exception/`

## Roles

```java
public enum Role { ADMIN, OFFICER, CLIENT }
```

- `ADMIN` → configura organización, áreas, usuarios, diseña políticas, monitorea
- `OFFICER` → inicia y atiende trámites, ejecuta tareas de su bandeja
- `CLIENT` → solo accede a `/api/mobile/**`
- `roles: List<Role>` en el documento `User` (múltiples roles permitidos)

## MongoDB

- URI via `MONGODB_URI` en `.env`
- MongoDB Atlas en producción; Docker local en desarrollo
- **No usar diseño relacional.** Embeber lo que siempre se lee junto.
- `workflow_policies`: nodos, transiciones, formularios y swimlanes embebidos
- `procedures`: `policySnapshot` embebido para proteger ante versionado
- `procedure_history`: colección separada para eventos (crece sin límite)
- `tasks`: separado de procedures; campo `taskAudience: INTERNAL|CLIENT`

## Motor de Workflow

- Vive en `WorkflowEngineService` (feature `procedure/application/`)
- Se activa cuando un funcionario completa una tarea
- Registra eventos en `procedure_history` (no en `procedures`)
- Al nodo `CLIENT_TASK`: crea tarea con `taskAudience=CLIENT`, pone `procedure.status=WAITING_CLIENT`
- Al completar `CLIENT_TASK`: `procedure.status` vuelve a `IN_PROGRESS`

## FastAPI

- Solo Spring Boot llama a FastAPI (`FASTAPI_BASE_URL` en `.env`)
- Angular y Flutter **nunca** llaman a FastAPI directamente
- No implementar lógica de IA dentro de este repositorio

## Seguridad

- JWT Bearer token en header `Authorization`
- `@PreAuthorize("hasAnyRole('ADMIN')")` para proteger endpoints
- Endpoints `/api/mobile/**` solo para rol `CLIENT`
- Validar siempre `task.assignedClientId == currentUser.clientId` antes de escrituras móviles
- CORS configurado para `http://localhost:4200`

## Variables de Entorno (`.env.example`)

```env
MONGODB_URI=mongodb+srv://...
JWT_SECRET=<min_256bit>
JWT_EXPIRATION_MS=86400000
FASTAPI_BASE_URL=http://localhost:8001
WS_ALLOWED_ORIGINS=http://localhost:4200
```

## Convenciones

- Manejo de errores con `@ControllerAdvice` en `exception/GlobalExceptionHandler`
- No lanzar excepciones genéricas; usar `ResourceNotFoundException` y `BusinessException`
- Paginación en listados grandes
- Prefijo de rutas: `/api/` (web) y `/api/mobile/` (Flutter)
