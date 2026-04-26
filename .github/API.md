# API Reference — SW1 Backend (Spring Boot)

Base URL: `http://localhost:8080`  
Autenticación: `Authorization: Bearer <jwt>`

---

## Auth

### POST /api/auth/login
**Descripción:** Autentica un usuario y devuelve un JWT.  
**Roles permitidos:** Público  
**Request body:** `LoginRequest { username, password }`  
**Response:** `LoginResponse { token, userId, username, roles }`  
**Códigos:** `200 OK`, `401 Unauthorized`

### POST /api/auth/register
**Descripción:** Registra un nuevo usuario (solo ADMIN).  
**Roles permitidos:** ADMIN  
**Request body:** `RegisterRequest { username, email, password, fullName, roles, positionName, phone, areaId, organizationId }`  
**Response:** `UserResponse { id, username, email, fullName, roles, positionName, phone, areaId, organizationId, clientId, enabled, createdAt }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`, `409 Conflict`

### GET /api/auth/me
**Descripción:** Devuelve el perfil del usuario autenticado.  
**Roles permitidos:** Autenticado  
**Response:** `UserResponse { id, username, email, fullName, roles, ... }`  
**Códigos:** `200 OK`, `401 Unauthorized`

---

## Organization

### POST /api/organizations
**Descripción:** Crea una nueva organización.  
**Roles permitidos:** ADMIN  
**Request body:** `CreateOrganizationRequest { name, description }`  
**Response:** `OrganizationResponse { id, name, description, areas, createdAt }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`

### GET /api/organizations
**Descripción:** Lista todas las organizaciones.  
**Roles permitidos:** ADMIN  
**Response:** `List<OrganizationResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/organizations/{id}
**Descripción:** Obtiene una organización por ID.  
**Roles permitidos:** ADMIN  
**Response:** `OrganizationResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### PUT /api/organizations/{id}
**Descripción:** Actualiza una organización.  
**Roles permitidos:** ADMIN  
**Request body:** `CreateOrganizationRequest { name, description }`  
**Response:** `OrganizationResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`

### DELETE /api/organizations/{id}
**Descripción:** Elimina una organización.  
**Roles permitidos:** ADMIN  
**Códigos:** `204 No Content`, `401 Unauthorized`, `404 Not Found`

### POST /api/organizations/{id}/areas
**Descripción:** Agrega un área a la organización.  
**Roles permitidos:** ADMIN  
**Request body:** `CreateAreaRequest { name, description }`  
**Response:** `OrganizationResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`

### DELETE /api/organizations/{orgId}/areas/{areaId}
**Descripción:** Elimina un área de la organización.  
**Roles permitidos:** ADMIN  
**Response:** `OrganizationResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

---

## Users

### GET /api/users
**Descripción:** Lista usuarios con paginación.  
**Roles permitidos:** ADMIN  
**Response:** `Page<UserResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/users/{id}
**Descripción:** Obtiene un usuario por ID.  
**Roles permitidos:** ADMIN  
**Response:** `UserResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### PUT /api/users/{id}
**Descripción:** Actualiza los datos de un usuario.  
**Roles permitidos:** ADMIN  
**Request body:** `UpdateUserRequest { fullName, email, phone, positionName, roles, organizationId, areaId }`  
**Response:** `UserResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`

### PATCH /api/users/{id}/status?enabled=true|false
**Descripción:** Activa o desactiva un usuario.  
**Roles permitidos:** ADMIN  
**Response:** `UserResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### DELETE /api/users/{id}
**Descripción:** Elimina un usuario.  
**Roles permitidos:** ADMIN  
**Códigos:** `204 No Content`, `401 Unauthorized`, `404 Not Found`

---

## Clients

### POST /api/clients
**Descripción:** Registra un nuevo cliente externo.  
**Roles permitidos:** ADMIN, OFFICER  
**Request body:** `CreateClientRequest { organizationId, fullName, documentType, documentNumber, phone, email, address }`  
**Response:** `ClientResponse { id, organizationId, fullName, documentType, documentNumber, phone, email, address, userId, createdBy, createdAt }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`, `409 Conflict`

### GET /api/clients?organizationId=
**Descripción:** Lista clientes de una organización.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `List<ClientResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/clients/{id}
**Descripción:** Obtiene un cliente por ID.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `ClientResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### GET /api/clients/search?documentNumber=&organizationId=
**Descripción:** Busca un cliente por número de documento dentro de una organización.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `ClientResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### PUT /api/clients/{id}
**Descripción:** Actualiza los datos de un cliente.  
**Roles permitidos:** ADMIN, OFFICER  
**Request body:** `UpdateClientRequest { fullName, phone, email, address }`  
**Response:** `ClientResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`

---

## Policies

### POST /api/policies
**Descripción:** Crea una nueva política de workflow en estado DRAFT.  
**Roles permitidos:** ADMIN  
**Request body:** `CreatePolicyRequest { organizationId, policyKey, name, description, allowedStartChannels }`  
**Response:** `PolicyResponse { id, organizationId, policyKey, name, description, version, status, allowedStartChannels, nodes, transitions, swimlanes, ... }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`, `409 Conflict`

### GET /api/policies?organizationId=
**Descripción:** Lista políticas de una organización con paginación.  
**Roles permitidos:** ADMIN  
**Response:** `Page<PolicySummaryResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/policies/{id}
**Descripción:** Obtiene el detalle completo de una política.  
**Roles permitidos:** ADMIN  
**Response:** `PolicyResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### PUT /api/policies/{id}/diagram
**Descripción:** Actualiza el diagrama/nodos/transiciones de una política DRAFT.  
**Roles permitidos:** ADMIN  
**Request body:** `DiagramUpdateRequest { diagram, nodes, transitions, swimlanes }`  
**Response:** `PolicyResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `422 Unprocessable Entity`

### POST /api/policies/{id}/publish
**Descripción:** Publica una política DRAFT (valida estructura, archiva versión anterior PUBLISHED).  
**Roles permitidos:** ADMIN  
**Response:** `PolicyResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`, `422 Unprocessable Entity`

### POST /api/policies/new-version?organizationId=&policyKey=
**Descripción:** Crea una nueva versión DRAFT copiando la última versión existente.  
**Roles permitidos:** ADMIN  
**Response:** `PolicyResponse`  
**Códigos:** `201 Created`, `401 Unauthorized`, `404 Not Found`

### POST /api/policies/{id}/archive
**Descripción:** Archiva una política.  
**Roles permitidos:** ADMIN  
**Response:** `PolicyResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

---

## Procedures

### POST /api/procedures
**Descripción:** Inicia un trámite por canal WEB.  
**Roles permitidos:** ADMIN, OFFICER  
**Request body:** `StartProcedureRequest { policyId, clientId, organizationId }`  
**Response:** `ProcedureResponse { id, organizationId, clientId, startedBy, currentNodeId, status, policySnapshot, formData, startChannel, createdAt, updatedAt }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`, `422 Unprocessable Entity`

### GET /api/procedures?organizationId=[&status=]
**Descripción:** Lista trámites de una organización con paginación, filtro opcional por status.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `Page<ProcedureSummaryResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/procedures/{id}
**Descripción:** Detalle de un trámite.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `ProcedureResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### GET /api/procedures/{id}/history
**Descripción:** Historial de eventos de un trámite.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `List<ProcedureHistory>`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

---

## Tasks

### GET /api/tasks?areaId=
**Descripción:** Lista tareas PENDING de un área (bandeja de área).  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `Page<TaskResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/tasks/mine
**Descripción:** Lista tareas IN_PROGRESS asignadas al OFFICER autenticado.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `Page<TaskResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/tasks/{id}
**Descripción:** Detalle de una tarea.  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `TaskResponse { id, procedureId, nodeId, nodeLabel, organizationId, areaId, taskAudience, status, assignedOfficerId, assignedClientId, form, formResponse, notes, createdAt, completedAt }`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### POST /api/tasks/{id}/claim
**Descripción:** El OFFICER toma una tarea PENDING (asignación).  
**Roles permitidos:** ADMIN, OFFICER  
**Response:** `TaskResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`, `422 Unprocessable Entity`

### POST /api/tasks/{id}/complete
**Descripción:** El OFFICER completa una tarea IN_PROGRESS y el motor avanza el trámite.  
**Roles permitidos:** ADMIN, OFFICER  
**Request body:** `CompleteTaskRequest { formResponse, notes }`  
**Response:** `TaskResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `422 Unprocessable Entity`

---

## Notifications

### POST /api/notifications
**Descripción:** Crea una notificación y la envía por WebSocket al destinatario.  
**Roles permitidos:** ADMIN, OFFICER  
**Request body:** `CreateNotificationRequest { organizationId, recipientId, type, title, message, procedureId, taskId }`  
**Response:** `NotificationResponse { id, organizationId, recipientId, type, title, message, procedureId, taskId, read, createdAt, readAt }`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`

### GET /api/notifications/mine[?unreadOnly=true]
**Descripción:** Lista notificaciones del usuario autenticado con paginación.  
**Roles permitidos:** Autenticado  
**Response:** `Page<NotificationResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/notifications/mine/unread-count
**Descripción:** Cuenta de notificaciones no leídas del usuario autenticado.  
**Roles permitidos:** Autenticado  
**Response:** `{ count: long }`  
**Códigos:** `200 OK`, `401 Unauthorized`

### PATCH /api/notifications/{id}/read
**Descripción:** Marca una notificación como leída.  
**Roles permitidos:** Autenticado  
**Response:** `NotificationResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### POST /api/notifications/mine/read-all
**Descripción:** Marca todas las notificaciones no leídas del usuario como leídas.  
**Roles permitidos:** Autenticado  
**Códigos:** `204 No Content`, `401 Unauthorized`

---

## Mobile

### GET /api/mobile/procedures
**Descripción:** Lista los trámites del cliente autenticado.  
**Roles permitidos:** CLIENT  
**Response:** `Page<ProcedureSummaryResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### GET /api/mobile/procedures/{id}
**Descripción:** Detalle de un trámite del cliente autenticado.  
**Roles permitidos:** CLIENT  
**Response:** `ProcedureResponse`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### GET /api/mobile/procedures/{id}/history
**Descripción:** Historial de eventos de un trámite del cliente.  
**Roles permitidos:** CLIENT  
**Response:** `List<ProcedureHistory>`  
**Códigos:** `200 OK`, `401 Unauthorized`, `404 Not Found`

### POST /api/mobile/procedures
**Descripción:** Inicia un trámite por canal MOBILE (solo si la política lo permite).  
**Roles permitidos:** CLIENT  
**Request body:** `StartProcedureRequest { policyId, clientId, organizationId }`  
**Response:** `ProcedureResponse`  
**Códigos:** `201 Created`, `400 Bad Request`, `401 Unauthorized`, `422 Unprocessable Entity`

### GET /api/mobile/tasks
**Descripción:** Lista CLIENT_TASKs pendientes asignadas al cliente autenticado.  
**Roles permitidos:** CLIENT  
**Response:** `Page<TaskResponse>`  
**Códigos:** `200 OK`, `401 Unauthorized`

### POST /api/mobile/tasks/{id}/complete
**Descripción:** El cliente completa una CLIENT_TASK y el motor avanza el trámite.  
**Roles permitidos:** CLIENT  
**Request body:** `CompleteTaskRequest { formResponse, notes }`  
**Response:** `TaskResponse`  
**Códigos:** `200 OK`, `400 Bad Request`, `401 Unauthorized`, `404 Not Found`, `422 Unprocessable Entity`
