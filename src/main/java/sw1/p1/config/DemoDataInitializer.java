package sw1.p1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sw1.p1.auth.domain.Role;
import sw1.p1.auth.domain.User;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.organization.domain.Area;
import sw1.p1.organization.domain.Organization;
import sw1.p1.organization.domain.OrganizationRepository;
import sw1.p1.policy.domain.FormDefinition;
import sw1.p1.policy.domain.FormField;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowPolicy;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.procedure.domain.PolicySnapshot;
import sw1.p1.procedure.domain.Procedure;
import sw1.p1.procedure.domain.ProcedureHistory;
import sw1.p1.procedure.domain.ProcedureHistoryRepository;
import sw1.p1.procedure.domain.ProcedureRepository;
import sw1.p1.policy.domain.WorkflowPolicyRepository;
import sw1.p1.shared.NodeType;
import sw1.p1.shared.PolicyStatus;
import sw1.p1.shared.ProcedureStatus;
import sw1.p1.shared.TaskAudience;
import sw1.p1.shared.TaskStatus;
import sw1.p1.task.domain.Task;
import sw1.p1.task.domain.TaskRepository;
import sw1.p1.client.domain.Client;
import sw1.p1.client.domain.ClientRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seed de datos demo para defensa del proyecto.
 * Solo se ejecuta si la base de datos no tiene organización demo.
 * Corre DESPUÉS del DataInitializer gracias a @Order(2).
 */
@Slf4j
@Component
@Order(2)
@Profile("!test")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WorkflowPolicyRepository workflowPolicyRepository;
    private final ProcedureRepository procedureRepository;
    private final ProcedureHistoryRepository procedureHistoryRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.password:}")
    private String demoPassword;

    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;

    @Override
    public void run(ApplicationArguments args) {
        Optional<Organization> existingOrg = organizationRepository.findByName("Banco Demo S.A.");
        if (existingOrg.isPresent()) {
            // Asegurar que el admin tiene organizationId (por email, más confiable que buscar por rol)
            final String existingOrgId = existingOrg.get().getId();
            userRepository.findByEmail(adminEmail).ifPresent(admin -> {
                if (admin.getOrganizationId() == null || admin.getOrganizationId().isBlank()) {
                    admin.setOrganizationId(existingOrgId);
                    userRepository.save(admin);
                    log.info("Admin '{}' vinculado a organización demo: {}", adminEmail, existingOrgId);
                } else {
                    log.info("Admin '{}' ya tiene organizationId: {}", adminEmail, admin.getOrganizationId());
                }
            });

            // Sembrar políticas si la org existe pero no tiene ninguna política
            List<Area> orgAreas = existingOrg.get().getAreas();
            String atencionId = (orgAreas != null && !orgAreas.isEmpty()) ? orgAreas.get(0).getId() : "area-atencion-demo";
            String revisionId = (orgAreas != null && orgAreas.size() > 1) ? orgAreas.get(1).getId() : atencionId;
            String analisisId = (orgAreas != null && orgAreas.size() > 2) ? orgAreas.get(2).getId() : atencionId;
            String creatorId = userRepository.findByEmail(adminEmail).map(User::getId).orElse("system");

            if (workflowPolicyRepository.findByOrganizationIdAndStatus(existingOrgId, PolicyStatus.PUBLISHED).isEmpty()) {
                log.info("Sembrando políticas demo para organización existente...");
                seedPoliciesDemo(existingOrgId, atencionId, revisionId, analisisId, creatorId);
            }

            // Officers extra (idempotente)
            seedExtraOfficers(existingOrgId, revisionId, analisisId);

            // Vincular cliente demo (idempotente, siempre se ejecuta)
            ensureClientLinked(existingOrgId);

            // Corregir tarea demo mal sembrada (IN_PROGRESS sin asignado → PENDING)
            taskRepository.findByProcedureIdAndNodeId(
                    procedureRepository.findByOrganizationId(existingOrgId, Pageable.unpaged())
                            .stream().filter(p -> "TRM-2026-0001".equals(p.getCode()))
                            .findFirst().map(p -> p.getId()).orElse("__none__"),
                    "node-form2"
            ).stream()
            .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS && t.getAssignedUserId() == null)
            .forEach(t -> {
                t.setStatus(TaskStatus.PENDING);
                taskRepository.save(t);
                log.info("Tarea demo TRM-2026-0001/node-form2 corregida a PENDING.");
            });

            log.info("Datos demo ya existen, omitiendo seed completo.");
            return;
        }

        log.info("Creando datos demo para defensa...");

        // ── 1. Áreas ──────────────────────────────────────────────────────────────
        String areaAtencionId = "area-atencion-demo";
        String areaRevisionId = "area-revision-demo";
        String areaAnalisisId = "area-analisis-demo";

        Area areaAtencion = Area.builder()
                .id(areaAtencionId)
                .name("Atención al Cliente")
                .description("Área de recepción y atención a clientes")
                .build();

        Area areaRevision = Area.builder()
                .id(areaRevisionId)
                .name("Revisión Técnica")
                .description("Área de revisión técnica de solicitudes")
                .build();

        Area areaAnalisis = Area.builder()
                .id(areaAnalisisId)
                .name("Análisis Crediticio")
                .description("Área de análisis y evaluación crediticia")
                .build();

        // ── 2. Organización ───────────────────────────────────────────────────────
        Organization org = Organization.builder()
                .name("Banco Demo S.A.")
                .businessType("Financiero")
                .ruc("1234567890001")
                .active(true)
                .areas(List.of(areaAtencion, areaRevision, areaAnalisis))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        org = organizationRepository.save(org);
        final String orgId = org.getId();
        log.info("Organización demo creada: {} ({})", org.getName(), orgId);

        // ── 3. Actualizar admin si no tiene organizationId ─────────────────────
        userRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().contains(Role.ADMIN)
                        && (u.getOrganizationId() == null || u.getOrganizationId().isBlank()))
                .findFirst()
                .ifPresent(admin -> {
                    admin.setOrganizationId(orgId);
                    userRepository.save(admin);
                    log.info("Admin actualizado con organizationId: {}", orgId);
                });

        // ── 4. Usuarios demo ──────────────────────────────────────────────────────
        User officer = User.builder()
                .email("officer@demo.com")
                .password(passwordEncoder.encode(demoPassword))
                .fullName("Juan Pérez")
                .roles(List.of(Role.OFFICER))
                .areaId(areaAtencionId)
                .organizationId(orgId)
                .active(true)
                .createdAt(Instant.now())
                .build();
        officer = userRepository.save(officer);
        final String officerId = officer.getId();

        User client = User.builder()
                .email("cliente@demo.com")
                .password(passwordEncoder.encode(demoPassword))
                .fullName("María García")
                .roles(List.of(Role.CLIENT))
                .active(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(client);
        log.info("Usuarios demo creados: officer={}, client", officerId);

        // ── 5. Política 1: Solicitud de Crédito Personal ─────────────────────────
        FormDefinition formRecepcion = FormDefinition.builder()
                .formId("form-recepcion-credito")
                .fields(List.of(
                        FormField.builder()
                                .fieldId("solicitante_nombre")
                                .type("TEXT")
                                .label("Nombre del solicitante")
                                .required(true)
                                .build(),
                        FormField.builder()
                                .fieldId("monto_solicitado")
                                .type("NUMBER")
                                .label("Monto solicitado (Bs)")
                                .required(true)
                                .min(1000.0)
                                .max(100000.0)
                                .build(),
                        FormField.builder()
                                .fieldId("plazo_meses")
                                .type("SELECT")
                                .label("Plazo (meses)")
                                .required(true)
                                .options(List.of("12", "24", "36", "48", "60"))
                                .build()
                ))
                .build();

        FormDefinition formAnalisis = FormDefinition.builder()
                .formId("form-analisis-credito")
                .fields(List.of(
                        FormField.builder()
                                .fieldId("score_crediticio")
                                .type("NUMBER")
                                .label("Score crediticio")
                                .required(true)
                                .min(0.0)
                                .max(1000.0)
                                .build(),
                        FormField.builder()
                                .fieldId("decision")
                                .type("SELECT")
                                .label("Decisión")
                                .required(true)
                                .options(List.of("APROBADO", "RECHAZADO"))
                                .build()
                ))
                .build();

        List<WorkflowNode> nodesCredito = List.of(
                WorkflowNode.builder().nodeId("node-start").type(NodeType.START).label("Inicio").build(),
                WorkflowNode.builder().nodeId("node-form1").type(NodeType.MANUAL_FORM)
                        .label("Recepción de Solicitud").departmentId(areaAtencionId).slaHours(24)
                        .form(formRecepcion).build(),
                WorkflowNode.builder().nodeId("node-cond1").type(NodeType.CONDITION)
                        .label("Evaluar monto").build(),
                WorkflowNode.builder().nodeId("node-form2").type(NodeType.MANUAL_FORM)
                        .label("Análisis Crediticio").departmentId(areaAnalisisId).slaHours(48)
                        .form(formAnalisis).build(),
                WorkflowNode.builder().nodeId("node-end-ok").type(NodeType.END)
                        .label("Crédito Aprobado").build(),
                WorkflowNode.builder().nodeId("node-end-nok").type(NodeType.END)
                        .label("Crédito Rechazado").build()
        );

        List<WorkflowTransition> transitionsCredito = List.of(
                WorkflowTransition.builder().transitionId("t1").from("node-start").to("node-form1").build(),
                WorkflowTransition.builder().transitionId("t2").from("node-form1").to("node-cond1").build(),
                WorkflowTransition.builder().transitionId("t3").from("node-cond1").to("node-form2")
                        .condition("monto_solicitado <= 50000").label("Requiere análisis").build(),
                WorkflowTransition.builder().transitionId("t4").from("node-cond1").to("node-end-ok")
                        .condition("monto_solicitado > 50000").label("Monto alto, aprobación directa").build(),
                WorkflowTransition.builder().transitionId("t5").from("node-form2").to("node-end-ok")
                        .condition("decision == APROBADO").build(),
                WorkflowTransition.builder().transitionId("t6").from("node-form2").to("node-end-nok")
                        .condition("decision == RECHAZADO").build()
        );

        WorkflowPolicy politicaCredito = WorkflowPolicy.builder()
                .organizationId(orgId)
                .policyKey("credito_personal")
                .name("Solicitud de Crédito Personal")
                .description("Proceso de solicitud y evaluación de crédito personal")
                .version(1)
                .status(PolicyStatus.PUBLISHED)
                .allowedStartChannels(List.of("WEB", "MOBILE"))
                .nodes(nodesCredito)
                .transitions(transitionsCredito)
                .createdBy(officerId)
                .publishedBy(officerId)
                .publishedAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .build();

        politicaCredito = workflowPolicyRepository.save(politicaCredito);
        final String polCreditoId = politicaCredito.getId();
        log.info("Política 'Solicitud de Crédito' creada: {}", polCreditoId);

        // ── 6. Política 2: Reclamo de Servicio ────────────────────────────────────
        FormDefinition formReclamo = FormDefinition.builder()
                .formId("form-recepcion-reclamo")
                .fields(List.of(
                        FormField.builder()
                                .fieldId("tipo_reclamo")
                                .type("SELECT")
                                .label("Tipo de reclamo")
                                .required(true)
                                .options(List.of("Cobro incorrecto", "Servicio deficiente", "Error en cuenta", "Otro"))
                                .build(),
                        FormField.builder()
                                .fieldId("descripcion")
                                .type("TEXTAREA")
                                .label("Descripción del problema")
                                .required(true)
                                .build()
                ))
                .build();

        FormDefinition formResolucion = FormDefinition.builder()
                .formId("form-resolucion-reclamo")
                .fields(List.of(
                        FormField.builder()
                                .fieldId("resultado")
                                .type("SELECT")
                                .label("Resultado")
                                .required(true)
                                .options(List.of("PROCEDENTE", "IMPROCEDENTE"))
                                .build(),
                        FormField.builder()
                                .fieldId("observacion")
                                .type("TEXTAREA")
                                .label("Observaciones")
                                .required(true)
                                .build()
                ))
                .build();

        List<WorkflowNode> nodesReclamo = List.of(
                WorkflowNode.builder().nodeId("node-start").type(NodeType.START).label("Inicio").build(),
                WorkflowNode.builder().nodeId("node-recepcion").type(NodeType.MANUAL_FORM)
                        .label("Recepción del Reclamo").departmentId(areaAtencionId).slaHours(8)
                        .form(formReclamo).build(),
                WorkflowNode.builder().nodeId("node-cliente").type(NodeType.CLIENT_TASK)
                        .label("Adjuntar evidencia").build(),
                WorkflowNode.builder().nodeId("node-revision").type(NodeType.MANUAL_FORM)
                        .label("Revisión y Resolución").departmentId(areaRevisionId).slaHours(72)
                        .form(formResolucion).build(),
                WorkflowNode.builder().nodeId("node-end").type(NodeType.END)
                        .label("Reclamo Resuelto").build()
        );

        List<WorkflowTransition> transitionsReclamo = List.of(
                WorkflowTransition.builder().transitionId("t1").from("node-start").to("node-recepcion").build(),
                WorkflowTransition.builder().transitionId("t2").from("node-recepcion").to("node-cliente").build(),
                WorkflowTransition.builder().transitionId("t3").from("node-cliente").to("node-revision").build(),
                WorkflowTransition.builder().transitionId("t4").from("node-revision").to("node-end").build()
        );

        WorkflowPolicy politicaReclamo = WorkflowPolicy.builder()
                .organizationId(orgId)
                .policyKey("reclamo_servicio")
                .name("Reclamo de Servicio")
                .description("Proceso para gestión de reclamos de clientes")
                .version(1)
                .status(PolicyStatus.PUBLISHED)
                .allowedStartChannels(List.of("WEB", "MOBILE"))
                .nodes(nodesReclamo)
                .transitions(transitionsReclamo)
                .createdBy(officerId)
                .publishedBy(officerId)
                .publishedAt(Instant.now().minus(6, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(9, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(6, ChronoUnit.DAYS))
                .build();

        politicaReclamo = workflowPolicyRepository.save(politicaReclamo);
        final String polReclamoId = politicaReclamo.getId();
        log.info("Política 'Reclamo de Servicio' creada: {}", polReclamoId);

        // ── 7. Procedimiento 1: TRM-2026-0001 (IN_PROGRESS) ───────────────────────
        Instant startedAt1 = Instant.now().minus(3, ChronoUnit.DAYS);

        PolicySnapshot snapshotCredito = PolicySnapshot.builder()
                .policyId(polCreditoId)
                .policyKey("credito_personal")
                .policyName("Solicitud de Crédito Personal")
                .version(1)
                .status(PolicyStatus.PUBLISHED)
                .nodes(nodesCredito)
                .transitions(transitionsCredito)
                .snapshotAt(startedAt1)
                .build();

        Procedure proc1 = Procedure.builder()
                .code("TRM-2026-0001")
                .organizationId(orgId)
                .policyId(polCreditoId)
                .policyVersion(1)
                .startedBy(officerId)
                .requester(Procedure.RequesterInfo.builder()
                        .fullName("María García")
                        .documentType("CI")
                        .documentNumber("12345678")
                        .phone("70000001")
                        .build())
                .currentNodeIds(List.of("node-form2"))
                .status(ProcedureStatus.IN_PROGRESS)
                .policySnapshot(snapshotCredito)
                .startChannel("WEB")
                .startedAt(startedAt1)
                .createdAt(startedAt1)
                .updatedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        proc1 = procedureRepository.save(proc1);
        final String proc1Id = proc1.getId();

        // Historial procedimiento 1
        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-start").nodeLabel("Inicio")
                .eventType("NODE_STARTED").occurredAt(startedAt1).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-form1").nodeLabel("Recepción de Solicitud")
                .eventType("NODE_STARTED").occurredAt(startedAt1.plus(1, ChronoUnit.MINUTES)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-form1").nodeLabel("Recepción de Solicitud")
                .eventType("TASK_COMPLETED").userId(officerId)
                .notes("Solicitud recibida. Monto: 25000")
                .formData(Map.of("solicitante_nombre", "María García", "monto_solicitado", 25000, "plazo_meses", "24"))
                .occurredAt(startedAt1.plus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-cond1").nodeLabel("Evaluar monto")
                .eventType("NODE_STARTED").occurredAt(startedAt1.plus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-cond1").nodeLabel("Evaluar monto")
                .eventType("CONDITION_EVALUATED")
                .notes("Condición: monto_solicitado <= 50000 → true. Derivando a Análisis.")
                .occurredAt(startedAt1.plus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc1Id).nodeId("node-form2").nodeLabel("Análisis Crediticio")
                .eventType("NODE_STARTED").occurredAt(startedAt1.plus(2, ChronoUnit.HOURS)).build());

        // Tarea activa procedimiento 1
        Instant taskActivatedAt = Instant.now().minus(2, ChronoUnit.DAYS);

        taskRepository.save(Task.builder()
                .procedureId(proc1Id)
                .procedureCode("TRM-2026-0001")
                .policyId(polCreditoId)
                .nodeId("node-form2")
                .label("Análisis Crediticio")
                .organizationId(orgId)
                .assignedDepartmentId(areaAnalisisId)
                .taskAudience(TaskAudience.INTERNAL)
                .status(TaskStatus.PENDING)
                .createdAt(taskActivatedAt)
                .dueAt(taskActivatedAt.plus(72, ChronoUnit.HOURS))
                .updatedAt(taskActivatedAt)
                .build());

        log.info("Procedimiento 1 creado: TRM-2026-0001");

        // ── 8. Procedimiento 2: TRM-2026-0002 (COMPLETED) ────────────────────────
        Instant startedAt2 = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant completedAt2 = Instant.now().minus(1, ChronoUnit.DAYS);

        PolicySnapshot snapshotReclamo = PolicySnapshot.builder()
                .policyId(polReclamoId)
                .policyKey("reclamo_servicio")
                .policyName("Reclamo de Servicio")
                .version(1)
                .status(PolicyStatus.PUBLISHED)
                .nodes(nodesReclamo)
                .transitions(transitionsReclamo)
                .snapshotAt(startedAt2)
                .build();

        Procedure proc2 = Procedure.builder()
                .code("TRM-2026-0002")
                .organizationId(orgId)
                .policyId(polReclamoId)
                .policyVersion(1)
                .startedBy(officerId)
                .requester(Procedure.RequesterInfo.builder()
                        .fullName("Carlos López")
                        .documentType("CI")
                        .documentNumber("87654321")
                        .phone("70000002")
                        .build())
                .currentNodeIds(List.of("node-end"))
                .status(ProcedureStatus.COMPLETED)
                .policySnapshot(snapshotReclamo)
                .startChannel("MOBILE")
                .startedAt(startedAt2)
                .completedAt(completedAt2)
                .createdAt(startedAt2)
                .updatedAt(completedAt2)
                .build();

        proc2 = procedureRepository.save(proc2);
        final String proc2Id = proc2.getId();

        // Historial procedimiento 2
        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-start").nodeLabel("Inicio")
                .eventType("NODE_STARTED").occurredAt(startedAt2).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-recepcion").nodeLabel("Recepción del Reclamo")
                .eventType("NODE_STARTED").occurredAt(startedAt2.plus(30, ChronoUnit.MINUTES)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-recepcion").nodeLabel("Recepción del Reclamo")
                .eventType("TASK_COMPLETED").userId(officerId)
                .formData(Map.of("tipo_reclamo", "Cobro incorrecto",
                        "descripcion", "Me cobraron doble en mi tarjeta"))
                .occurredAt(startedAt2.plus(1, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-cliente").nodeLabel("Adjuntar evidencia")
                .eventType("CLIENT_TASK_CREATED").occurredAt(startedAt2.plus(1, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-cliente").nodeLabel("Adjuntar evidencia")
                .eventType("CLIENT_TASK_COMPLETED").notes("Cliente subió comprobante de cobro")
                .occurredAt(startedAt2.plus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-revision").nodeLabel("Revisión y Resolución")
                .eventType("NODE_STARTED").occurredAt(startedAt2.plus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-revision").nodeLabel("Revisión y Resolución")
                .eventType("TASK_COMPLETED").userId(officerId)
                .formData(Map.of("resultado", "PROCEDENTE",
                        "observacion", "Se verificó el cobro doble. Se procede al reembolso."))
                .occurredAt(completedAt2.minus(2, ChronoUnit.HOURS)).build());

        procedureHistoryRepository.save(ProcedureHistory.builder()
                .procedureId(proc2Id).nodeId("node-end").nodeLabel("Reclamo Resuelto")
                .eventType("STATUS_CHANGED").notes("Trámite completado exitosamente.")
                .occurredAt(completedAt2).build());

        log.info("Procedimiento 2 creado: TRM-2026-0002");

        // ── 9. Officers extra ─────────────────────────────────────────────────────
        seedExtraOfficers(orgId, areaRevisionId, areaAnalisisId);

        log.info("Seed demo completado exitosamente.");
    }

    private void seedPoliciesDemo(String orgId, String atencionId, String revisionId, String analisisId, String creatorId) {
        // ── Política 1: Solicitud de Crédito Personal ──────────────────────────
        FormDefinition formRecepcion = FormDefinition.builder()
                .formId("form-recepcion-credito")
                .fields(List.of(
                        FormField.builder().fieldId("solicitante_nombre").type("TEXT")
                                .label("Nombre del solicitante").required(true).build(),
                        FormField.builder().fieldId("monto_solicitado").type("NUMBER")
                                .label("Monto solicitado (Bs)").required(true).min(1000.0).max(100000.0).build(),
                        FormField.builder().fieldId("plazo_meses").type("SELECT")
                                .label("Plazo (meses)").required(true)
                                .options(List.of("12", "24", "36", "48", "60")).build()
                )).build();

        FormDefinition formAnalisis = FormDefinition.builder()
                .formId("form-analisis-credito")
                .fields(List.of(
                        FormField.builder().fieldId("score_crediticio").type("NUMBER")
                                .label("Score crediticio").required(true).min(0.0).max(1000.0).build(),
                        FormField.builder().fieldId("decision").type("SELECT")
                                .label("Decisión").required(true)
                                .options(List.of("APROBADO", "RECHAZADO")).build()
                )).build();

        List<WorkflowNode> nodesCredito = List.of(
                WorkflowNode.builder().nodeId("node-start").type(NodeType.START).label("Inicio").build(),
                WorkflowNode.builder().nodeId("node-form1").type(NodeType.MANUAL_FORM)
                        .label("Recepción de Solicitud").departmentId(atencionId).slaHours(24).form(formRecepcion).build(),
                WorkflowNode.builder().nodeId("node-cond1").type(NodeType.CONDITION).label("Evaluar monto").build(),
                WorkflowNode.builder().nodeId("node-form2").type(NodeType.MANUAL_FORM)
                        .label("Análisis Crediticio").departmentId(analisisId).slaHours(48).form(formAnalisis).build(),
                WorkflowNode.builder().nodeId("node-end-ok").type(NodeType.END).label("Crédito Aprobado").build(),
                WorkflowNode.builder().nodeId("node-end-nok").type(NodeType.END).label("Crédito Rechazado").build()
        );

        List<WorkflowTransition> transitionsCredito = List.of(
                WorkflowTransition.builder().transitionId("t1").from("node-start").to("node-form1").build(),
                WorkflowTransition.builder().transitionId("t2").from("node-form1").to("node-cond1").build(),
                WorkflowTransition.builder().transitionId("t3").from("node-cond1").to("node-form2")
                        .condition("monto_solicitado <= 50000").label("Requiere análisis").build(),
                WorkflowTransition.builder().transitionId("t4").from("node-cond1").to("node-end-ok")
                        .condition("monto_solicitado > 50000").label("Monto alto, aprobación directa").build(),
                WorkflowTransition.builder().transitionId("t5").from("node-form2").to("node-end-ok")
                        .condition("decision == APROBADO").build(),
                WorkflowTransition.builder().transitionId("t6").from("node-form2").to("node-end-nok")
                        .condition("decision == RECHAZADO").build()
        );

        workflowPolicyRepository.save(WorkflowPolicy.builder()
                .organizationId(orgId).policyKey("credito_personal")
                .name("Solicitud de Crédito Personal")
                .description("Proceso de solicitud y evaluación de crédito personal")
                .version(1).status(PolicyStatus.PUBLISHED)
                .allowedStartChannels(List.of("WEB", "MOBILE"))
                .nodes(nodesCredito).transitions(transitionsCredito)
                .createdBy(creatorId).publishedBy(creatorId)
                .publishedAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .build());
        log.info("Política 'Solicitud de Crédito' sembrada.");

        // ── Política 2: Reclamo de Servicio ──────────────────────────────────────
        FormDefinition formReclamo = FormDefinition.builder()
                .formId("form-recepcion-reclamo")
                .fields(List.of(
                        FormField.builder().fieldId("tipo_reclamo").type("SELECT")
                                .label("Tipo de reclamo").required(true)
                                .options(List.of("Cobro incorrecto", "Servicio deficiente", "Error en cuenta", "Otro")).build(),
                        FormField.builder().fieldId("descripcion").type("TEXTAREA")
                                .label("Descripción del problema").required(true).build()
                )).build();

        FormDefinition formResolucion = FormDefinition.builder()
                .formId("form-resolucion-reclamo")
                .fields(List.of(
                        FormField.builder().fieldId("resultado").type("SELECT")
                                .label("Resultado").required(true)
                                .options(List.of("PROCEDENTE", "IMPROCEDENTE")).build(),
                        FormField.builder().fieldId("observacion").type("TEXTAREA")
                                .label("Observaciones").required(true).build()
                )).build();

        List<WorkflowNode> nodesReclamo = List.of(
                WorkflowNode.builder().nodeId("node-start").type(NodeType.START).label("Inicio").build(),
                WorkflowNode.builder().nodeId("node-recepcion").type(NodeType.MANUAL_FORM)
                        .label("Recepción del Reclamo").departmentId(atencionId).slaHours(8).form(formReclamo).build(),
                WorkflowNode.builder().nodeId("node-cliente").type(NodeType.CLIENT_TASK)
                        .label("Adjuntar evidencia").build(),
                WorkflowNode.builder().nodeId("node-revision").type(NodeType.MANUAL_FORM)
                        .label("Revisión y Resolución").departmentId(revisionId).slaHours(72).form(formResolucion).build(),
                WorkflowNode.builder().nodeId("node-end").type(NodeType.END).label("Reclamo Resuelto").build()
        );

        List<WorkflowTransition> transitionsReclamo = List.of(
                WorkflowTransition.builder().transitionId("t1").from("node-start").to("node-recepcion").build(),
                WorkflowTransition.builder().transitionId("t2").from("node-recepcion").to("node-cliente").build(),
                WorkflowTransition.builder().transitionId("t3").from("node-cliente").to("node-revision").build(),
                WorkflowTransition.builder().transitionId("t4").from("node-revision").to("node-end").build()
        );

        workflowPolicyRepository.save(WorkflowPolicy.builder()
                .organizationId(orgId).policyKey("reclamo_servicio")
                .name("Reclamo de Servicio")
                .description("Proceso para gestión de reclamos de clientes")
                .version(1).status(PolicyStatus.PUBLISHED)
                .allowedStartChannels(List.of("WEB", "MOBILE"))
                .nodes(nodesReclamo).transitions(transitionsReclamo)
                .createdBy(creatorId).publishedBy(creatorId)
                .publishedAt(Instant.now().minus(6, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(9, ChronoUnit.DAYS))
                .updatedAt(Instant.now().minus(6, ChronoUnit.DAYS))
                .build());
        log.info("Política 'Reclamo de Servicio' sembrada.");
    }

    // ── Officers extra (idempotente por email) ────────────────────────────────
    private void seedExtraOfficers(String orgId, String revisionId, String analisisId) {
        if (userRepository.findByEmail("officer.revision@demo.com").isEmpty()) {
            userRepository.save(User.builder()
                    .email("officer.revision@demo.com")
                    .password(passwordEncoder.encode(demoPassword))
                    .fullName("Juan Mamani")
                    .roles(List.of(Role.OFFICER))
                    .areaId(revisionId)
                    .organizationId(orgId)
                    .active(true)
                    .createdAt(Instant.now())
                    .build());
            log.info("Officer 'officer.revision@demo.com' creado (Revisión Técnica).");
        }
        if (userRepository.findByEmail("officer.analisis@demo.com").isEmpty()) {
            userRepository.save(User.builder()
                    .email("officer.analisis@demo.com")
                    .password(passwordEncoder.encode(demoPassword))
                    .fullName("Ana Torres")
                    .roles(List.of(Role.OFFICER))
                    .areaId(analisisId)
                    .organizationId(orgId)
                    .active(true)
                    .createdAt(Instant.now())
                    .build());
            log.info("Officer 'officer.analisis@demo.com' creado (Análisis Crediticio).");
        }
    }

    private void ensureClientLinked(String orgId) {
        var user = userRepository.findByEmail("cliente@demo.com").orElse(null);
        if (user == null) {
            log.warn("No existe el usuario cliente@demo.com");
            return;
        }

        if (clientRepository.findByUserId(user.getId()).isPresent()) {
            log.info("Cliente demo ya vinculado: userId={}", user.getId());
            return;
        }

        Client client = clientRepository.findByDocumentNumberAndOrganizationId("87654321", orgId)
                .orElse(null);
        if (client == null) {
            client = Client.builder()
                    .userId(user.getId())
                    .organizationId(orgId)
                    .fullName("María García")
                    .documentType("CI")
                    .documentNumber("87654321")
                    .email("cliente@demo.com")
                    .phone("70000002")
                    .createdAt(Instant.now())
                    .build();
        } else {
            client.setUserId(user.getId());
        }
        clientRepository.save(client);
        user.setClientId(client.getId());
        userRepository.save(user);
        log.info("Cliente demo vinculado: userId={}, clientId={}", user.getId(), client.getId());
    }
}
