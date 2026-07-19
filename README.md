# SI2 G2 — Backend Spring Boot

[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![MongoDB](https://img.shields.io/badge/MongoDB-7.x-47A248.svg)](https://www.mongodb.com/)
[![AWS](https://img.shields.io/badge/AWS-S3_|_DynamoDB-FF9900.svg)](https://aws.amazon.com/)

Motor de workflow, gestión documental y API administrativa — UAGRM Sistemas de Información 2, Grupo 2.

---

## Stack

| Capa | Tecnología |
|---|---|
| Framework | Spring Boot 4.0.6 |
| Lenguaje | Java 21 |
| Base de datos | MongoDB (cluster0.aec6io6.mongodb.net) |
| Seguridad | Spring Security + JWT (jjwt 0.12.3) |
| Almacenamiento | AWS S3 |
| Auditoría | AWS DynamoDB (append-only) |
| Diseñador BPMN | bpmn-js 18.14.0 |
| Testing | JUnit 5 + Mockito |

---

## Requisitos previos

- JDK 21
- Maven 3.9+
- MongoDB (local o Atlas)
- AWS credentials configuradas (S3 + DynamoDB)

---

## Levantar el proyecto

```bash
cd sw1-1p-be-springboot

# Compilar
./mvnw clean compile

# Ejecutar tests (179 tests)
./mvnw test

# Levantar servidor (puerto 2804)
./mvnw spring-boot:run
```

---

## Configuración

El archivo `application.properties` en `src/main/resources/` contiene las propiedades de entorno. Variables sensibles se inyectan por entorno:

```properties
spring.data.mongodb.uri=${MONGODB_URI}
aws.accessKeyId=${AWS_ACCESS_KEY}
aws.secretAccessKey=${AWS_SECRET_KEY}
```

---

## Estructura del proyecto

```
src/main/java/sw1/p1/
├── auth/           — autenticación JWT
├── client/         — gestión de clientes
├── config/         — seguridad, S3, DynamoDB, demo data
├── document/       — gestión documental (S3, versionado)
├── exception/      — handler global de excepciones
├── form/           — FormTemplate + FormVersion (Fase 4)
├── notification/   — notificaciones internas
├── organization/   — organizaciones
├── policy/         — políticas BPMN, versionado, validación
├── procedure/      — motor de workflow, trámites, experiencia móvil
├── security/       — filtro JWT, user details
├── shared/         — enums, storage (S3), tipos comunes
├── task/           — tareas del workflow
└── user/           — gestión de usuarios
```

---

## Endpoints principales

| Módulo | Prefijo | Acceso |
|---|---|---|
| Auth | `/api/auth/**` | Público |
| Policies | `/api/policies/**` | ADMIN |
| Policy Versions | `/api/policies/{policyId}/versions/**` | ADMIN |
| Forms | `/api/admin/form-templates/**`, `/api/admin/form-versions/**` | ADMIN |
| Procedures | `/api/procedures/**` | ADMIN, OFFICER |
| Mobile | `/api/mobile/**` | CLIENT |
| Documents | `/api/documents/**` | ADMIN, OFFICER |
| Tasks | `/api/tasks/**` | OFFICER |

---

## Roles

| Rol | Funcionalidades |
|---|---|
| `ADMIN` | CRUD políticas, formularios, organizaciones, usuarios |
| `OFFICER` | Gestión de trámites, tareas, documentos |
| `CLIENT` | App móvil: consultar trámites, completar tareas |

---

## Fases implementadas

| Fase | Descripción | Tests |
|---|---|---|
| 1-2 | Workflow engine, JointJS designer, gestión documental | — |
| 3 | Migración bpmn-js, PolicyVersion, NodeConfiguration, ejecución BPMN, contrato móvil | 44 |
| 4.1 | FormTemplate + FormVersion versionados, 11 endpoints ADMIN, validación semántica, seguridad HTTP | 48 |
| 4.3 | Asociación de formularios, materialización y disponibilidad exacta de versiones publicadas | 87 |
| **Total** | | **179** |
