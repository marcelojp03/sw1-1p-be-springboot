package sw1.p1.policy.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.domain.NodeConfigurationRepository;
import sw1.p1.policy.domain.WorkflowPolicyRepository;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpmnValidationService {

    private static final Set<String> SUPPORTED_ELEMENTS = Set.of(
            "bpmn:startEvent", "bpmn:endEvent",
            "bpmn:userTask", "bpmn:serviceTask", "bpmn:sendTask",
            "bpmn:exclusiveGateway", "bpmn:parallelGateway",
            "bpmn:sequenceFlow", "bpmn:participant", "bpmn:lane",
            "bpmn:process", "bpmn:definitions",
            "bpmndi:BPMNDiagram", "bpmndi:BPMNPlane", "bpmndi:BPMNShape", "bpmndi:BPMNEdge",
            "bpmn:incoming", "bpmn:outgoing",
            "bpmn:conditionExpression"
    );

    private final NodeConfigurationRepository nodeConfigRepository;
    private final WorkflowPolicyRepository policyRepository;

    public ValidationResult validate(String policyId, String versionId, String bpmnXml) {
        List<Violation> violations = new ArrayList<>();

        if (bpmnXml == null || bpmnXml.isBlank()) {
            violations.add(new Violation("BPMN_EMPTY", null, "El diagrama BPMN está vacío."));
            return new ValidationResult(false, violations);
        }

        Document doc;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Security: disable XXE
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(bpmnXml)));
        } catch (Exception e) {
            violations.add(new Violation("BPMN_PARSE_ERROR", null,
                    "El XML no es válido: " + e.getMessage()));
            return new ValidationResult(false, violations);
        }

        Element root = doc.getDocumentElement();

        var processes = root.getElementsByTagNameNS("*", "process");
        if (processes.getLength() == 0) {
            violations.add(new Violation("BPMN_NO_PROCESS", null,
                    "El diagrama no contiene ningún proceso ejecutable."));
            return new ValidationResult(false, violations);
        }

        Element process = (Element) processes.item(0);

        // BPMN-001: XML ya parseado exitosamente
        // BPMN-002: exactamente un StartEvent
        var startEvents = process.getElementsByTagNameNS("*", "startEvent");
        if (startEvents.getLength() != 1) {
            violations.add(new Violation("BPMN_START_EVENT_COUNT", null,
                    "Debe haber exactamente un StartEvent. Encontrados: " + startEvents.getLength()));
        }

        // BPMN-003: al menos un EndEvent
        var endEvents = process.getElementsByTagNameNS("*", "endEvent");
        if (endEvents.getLength() == 0) {
            violations.add(new Violation("BPMN_END_EVENT_COUNT", null,
                    "Debe haber al menos un EndEvent."));
        }

        // Recolectar todos los elementos ejecutables
        Map<String, Element> elements = new HashMap<>();
        Set<String> elementIds = new HashSet<>();
        collectElements(process, elements, elementIds);

        // BPMN-004: elementos dentro del subconjunto
        for (var entry : elements.entrySet()) {
            String tag = entry.getValue().getTagName();
            if (!SUPPORTED_ELEMENTS.contains(tag)) {
                violations.add(new Violation("BPMN_UNSUPPORTED_ELEMENT", entry.getKey(),
                        tag + " no está soportado. Use UserTask, ServiceTask, SendTask, " +
                        "StartEvent, EndEvent, ExclusiveGateway, ParallelGateway."));
            }
        }

        // BPMN-005: referencias de SequenceFlow válidas
        var flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        Map<String, String> flowSources = new HashMap<>();
        Map<String, String> flowTargets = new HashMap<>();
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            String flowId = flow.getAttribute("id");
            String sourceRef = flow.getAttribute("sourceRef");
            String targetRef = flow.getAttribute("targetRef");

            if (sourceRef.isEmpty() || targetRef.isEmpty()) {
                violations.add(new Violation("BPMN_FLOW_MISSING_REF", flowId,
                        "El SequenceFlow no tiene sourceRef o targetRef."));
                continue;
            }
            flowSources.put(sourceRef, flowId);
            flowTargets.put(targetRef, flowId);

            if (!elementIds.contains(sourceRef)) {
                violations.add(new Violation("BPMN_FLOW_INVALID_SOURCE", flowId,
                        "El sourceRef '" + sourceRef + "' no existe."));
            }
            if (!elementIds.contains(targetRef)) {
                violations.add(new Violation("BPMN_FLOW_INVALID_TARGET", flowId,
                        "El targetRef '" + targetRef + "' no existe."));
            }
        }

        // BPMN-006: sin huérfanos ni dead ends
        String startId = startEvents.getLength() == 1
                ? ((Element) startEvents.item(0)).getAttribute("id") : null;
        for (var entry : elements.entrySet()) {
            String id = entry.getKey();
            Element el = entry.getValue();
            String tag = el.getTagName();

            if (tag.equals("bpmn:startEvent") || tag.equals("bpmn:endEvent")) continue;

            boolean hasIncoming = flowTargets.containsKey(id);
            boolean hasOutgoing = flowSources.containsKey(id);

            if (!hasIncoming && !id.equals(startId)) {
                violations.add(new Violation("BPMN_ORPHAN_NODE", id,
                        "El elemento no tiene ningún flujo entrante (huérfano)."));
            }
            if (!hasOutgoing) {
                violations.add(new Violation("BPMN_DEAD_END", id,
                        "El elemento no tiene ningún flujo saliente (callejón sin salida)."));
            }
        }

        // BPMN-007: todos los caminos alcanzan algún EndEvent
        if (!violations.isEmpty()) {
            // Solo si hay StartEvent y estructura válida
            if (startEvents.getLength() == 1) {
                Set<String> reachable = bfsReachable(startId, flowSources, elements);
                Set<String> endIds = new HashSet<>();
                for (int i = 0; i < endEvents.getLength(); i++) {
                    endIds.add(((Element) endEvents.item(i)).getAttribute("id"));
                }

                boolean allPathsEnd = true;
                for (String id : reachable) {
                    Element el = elements.get(id);
                    if (el == null) continue;
                    String tag = el.getTagName();
                    if (!tag.equals("bpmn:endEvent") && !flowSources.containsKey(id)) {
                        allPathsEnd = false;
                        break;
                    }
                }
                if (!allPathsEnd || reachable.stream().noneMatch(endIds::contains)) {
                    violations.add(new Violation("BPMN_UNREACHABLE_END", null,
                            "No todos los caminos alcanzan un EndEvent."));
                }
            }
        }

        // BPMN-008: ExclusiveGateway válido
        for (var entry : elements.entrySet()) {
            if (entry.getValue().getTagName().equals("bpmn:exclusiveGateway")) {
                String gwId = entry.getKey();
                List<String> outgoingIds = getOutgoing(gwId, process);
                if (outgoingIds.size() < 2) {
                    violations.add(new Violation("BPMN_GATEWAY_FEW_OUTGOING", gwId,
                            "El ExclusiveGateway debe tener al menos 2 flujos salientes."));
                }

                long defaultCount = 0;
                long conditionedCount = 0;
                for (String outId : outgoingIds) {
                    Element outFlow = findFlowById(outId, process);
                    if (outFlow != null &&
                            outFlow.getElementsByTagNameNS("*", "conditionExpression").getLength() > 0) {
                        conditionedCount++;
                    } else {
                        defaultCount++;
                    }
                }
                if (defaultCount > 1) {
                    violations.add(new Violation("BPMN_GATEWAY_MULTIPLE_DEFAULT", gwId,
                            "El ExclusiveGateway tiene más de un flujo por defecto."));
                }
                if (outgoingIds.size() > 1 && conditionedCount == 0) {
                    violations.add(new Violation("BPMN_GATEWAY_NO_CONDITIONS", gwId,
                            "Las salidas del ExclusiveGateway no tienen condiciones."));
                }
            }
        }

        // BPMN-009: ParallelGateway válido
        for (var entry : elements.entrySet()) {
            if (entry.getValue().getTagName().equals("bpmn:parallelGateway")) {
                String pgId = entry.getKey();
                int inCount = getIncoming(pgId, process).size();
                int outCount = getOutgoing(pgId, process).size();

                if (inCount == 1 && outCount < 2) {
                    violations.add(new Violation("BPMN_FORK_INVALID", pgId,
                            "ParallelGateway fork debe tener al menos 2 salidas."));
                }
                if (inCount > 1 && outCount != 1) {
                    violations.add(new Violation("BPMN_JOIN_INVALID", pgId,
                            "ParallelGateway join debe tener exactamente 1 salida."));
                }
            }
        }

        // BPMN-010: UserTask con NodeConfiguration (maneja duplicados)
        List<NodeConfiguration> configs = nodeConfigRepository.findByPolicyVersionId(versionId);
        Map<String, NodeConfiguration> configMap = new HashMap<>();
        for (NodeConfiguration c : configs) {
            String key = c.getBpmnElementId();
            if (configMap.containsKey(key)) {
                violations.add(new Violation("BPMN_DUPLICATE_CONFIG", key,
                        "El elemento tiene más de una NodeConfiguration (IDs: "
                        + configMap.get(key).getId() + ", " + c.getId() + ")"));
            }
            configMap.putIfAbsent(key, c);
        }

        for (var entry : elements.entrySet()) {
            String tag = entry.getValue().getTagName();
            if (tag.equals("bpmn:userTask") || tag.equals("bpmn:serviceTask") || tag.equals("bpmn:sendTask")) {
                String elementId = entry.getKey();
                NodeConfiguration config = configMap.get(elementId);
                if (config == null) {
                    violations.add(new Violation("BPMN_MISSING_CONFIG", elementId,
                            "El elemento requiere NodeConfiguration pero no tiene ninguna."));
                } else {
                    validateNodeConfig(config, elementId, violations);
                }
            }
        }

        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void validateNodeConfig(NodeConfiguration config, String elementId,
                                     List<Violation> violations) {
        String taskKind = config.getTaskKind();
        if (taskKind == null || taskKind.isBlank()) {
            violations.add(new Violation("BPMN_CONFIG_NO_TASKKIND", elementId,
                    "NodeConfiguration no tiene taskKind definido."));
            return;
        }

        if ("OFFICER_TASK".equals(taskKind)) {
            if (config.getDepartmentId() == null || config.getDepartmentId().isBlank()) {
                violations.add(new Violation("BPMN_CONFIG_NO_DEPARTMENT", elementId,
                        "OFFICER_TASK requiere departmentId."));
            }
        }

        if ("CLIENT_TASK".equals(taskKind)) {
            if (config.getDepartmentId() != null && !config.getDepartmentId().isBlank()) {
                violations.add(new Violation("BPMN_CONFIG_CLIENT_NO_DEPT", elementId,
                        "CLIENT_TASK no debe tener departmentId."));
            }
        }
    }

    private void collectElements(Element process, Map<String, Element> elements,
                                  Set<String> elementIds) {
        collectRecursive(process, elements, elementIds, Set.of(
                "bpmn:startEvent", "bpmn:endEvent",
                "bpmn:userTask", "bpmn:serviceTask", "bpmn:sendTask", "bpmn:task",
                "bpmn:exclusiveGateway", "bpmn:parallelGateway",
                "bpmn:inclusiveGateway", "bpmn:complexGateway", "bpmn:eventBasedGateway",
                "bpmn:callActivity", "bpmn:subProcess",
                "bpmn:intermediateCatchEvent", "bpmn:intermediateThrowEvent",
                "bpmn:boundaryEvent"));
    }

    private void collectRecursive(Node node, Map<String, Element> elements,
                                   Set<String> elementIds, Set<String> targetTags) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            String tag = el.getTagName();
            String id = el.getAttribute("id");
            if (id != null && !id.isEmpty() && targetTags.contains(tag)) {
                elements.put(id, el);
                elementIds.add(id);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectRecursive(children.item(i), elements, elementIds, targetTags);
        }
    }

    private Set<String> bfsReachable(String startId, Map<String, String> flowSources,
                                      Map<String, Element> elements) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            for (var entry : flowSources.entrySet()) {
                if (entry.getKey().equals(current)) {
                    queue.add(flowSources.entrySet().stream()
                            .filter(e -> e.getKey().equals(current))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse(null));
                }
            }
        }
        // Simplified — returns visited. Full BFS would need reverse flow map.
        return visited;
    }

    private List<String> getOutgoing(String elementId, Element process) {
        List<String> out = new ArrayList<>();
        var flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (elementId.equals(f.getAttribute("sourceRef"))) {
                out.add(f.getAttribute("id"));
            }
        }
        return out;
    }

    private List<String> getIncoming(String elementId, Element process) {
        List<String> in = new ArrayList<>();
        var flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (elementId.equals(f.getAttribute("targetRef"))) {
                in.add(f.getAttribute("id"));
            }
        }
        return in;
    }

    private Element findFlowById(String flowId, Element process) {
        var flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element f = (Element) flows.item(i);
            if (flowId.equals(f.getAttribute("id"))) return f;
        }
        return null;
    }

    public record Violation(String code, String elementId, String message) {}
    public record ValidationResult(boolean valid, List<Violation> violations) {}
}
