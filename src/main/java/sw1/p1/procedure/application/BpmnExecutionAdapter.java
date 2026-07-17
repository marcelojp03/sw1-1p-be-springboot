package sw1.p1.procedure.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import sw1.p1.exception.BusinessException;
import sw1.p1.policy.domain.NodeConfiguration;
import sw1.p1.policy.domain.PolicyVersion;
import sw1.p1.policy.domain.WorkflowNode;
import sw1.p1.policy.domain.WorkflowTransition;
import sw1.p1.shared.NodeType;
import sw1.p1.shared.PolicyVersionStatus;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

@Component
@Slf4j
public class BpmnExecutionAdapter {

    public record BpmnProcessDefinition(
            List<WorkflowNode> nodes,
            List<WorkflowTransition> transitions,
            Map<String, NodeConfiguration> configMap
    ) {}

    public BpmnProcessDefinition parse(PolicyVersion version, List<NodeConfiguration> configs) {
        if (version.getStatus() != PolicyVersionStatus.PUBLISHED) {
            throw new BusinessException("Solo se pueden ejecutar versiones PUBLICADAS");
        }

        String xml = version.getBpmnXml();
        if (xml == null || xml.isBlank()) {
            throw new BusinessException("La versión no tiene BPMN XML");
        }

        Document doc = parseXml(xml);
        Element process = findExecutableProcess(doc);

        Map<String, NodeConfiguration> configMap = new HashMap<>();
        for (NodeConfiguration c : configs) {
            NodeConfiguration prev = configMap.putIfAbsent(c.getBpmnElementId(), c);
            if (prev != null) {
                throw new BusinessException(
                        "NodeConfiguration duplicada para " + c.getBpmnElementId()
                        + " (IDs: " + prev.getId() + ", " + c.getId() + ")");
            }
        }

        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowTransition> transitions = new ArrayList<>();

        extractElements(process, nodes, transitions, configMap);
        log.info("BPMN adaptado: {} nodos, {} transiciones", nodes.size(), transitions.size());
        return new BpmnProcessDefinition(nodes, transitions, configMap);
    }

    private Document parseXml(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Security: disable XXE (same as BpmnValidationService)
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new BusinessException("Error al parsear BPMN XML: " + e.getMessage());
        }
    }

    private Element findExecutableProcess(Document doc) {
        var processes = doc.getDocumentElement().getElementsByTagNameNS("*", "process");
        Element executable = null;
        for (int i = 0; i < processes.getLength(); i++) {
            Element p = (Element) processes.item(i);
            if ("true".equals(p.getAttribute("isExecutable"))) {
                if (executable != null) {
                    throw new BusinessException("Múltiples procesos ejecutables no soportados");
                }
                executable = p;
            }
        }
        if (executable == null) {
            throw new BusinessException("No se encontró un proceso con isExecutable='true'");
        }
        return executable;
    }

    private void extractElements(Element process, List<WorkflowNode> nodes,
                                  List<WorkflowTransition> transitions,
                                  Map<String, NodeConfiguration> configMap) {
        Set<String> nodeIds = new HashSet<>();
        NodeList children = process.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) child;
            String localName = el.getLocalName();
            String id = el.getAttribute("id");
            if (id.isEmpty()) continue;

            NodeType type = mapToNodeType(localName, id, configMap);
            if (type == null) continue;

            nodeIds.add(id);
            nodes.add(WorkflowNode.builder()
                    .nodeId(id)
                    .type(type)
                    .label(getName(el))
                    .build());
        }

        var flows = process.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            String id = flow.getAttribute("id");
            String source = flow.getAttribute("sourceRef");
            String target = flow.getAttribute("targetRef");
            if (source.isEmpty() || target.isEmpty()) continue;
            if (!nodeIds.contains(source) || !nodeIds.contains(target)) {
                throw new BusinessException(
                        "SequenceFlow '" + id + "' referencia nodos inexistentes: "
                        + source + " → " + target);
            }

            transitions.add(WorkflowTransition.builder()
                    .transitionId(id)
                    .from(source)
                    .to(target)
                    .build());
        }
    }

    private NodeType mapToNodeType(String localName, String elementId,
                                    Map<String, NodeConfiguration> configMap) {
        return switch (localName) {
            case "startEvent" -> NodeType.START;
            case "endEvent" -> NodeType.END;
            case "userTask" -> {
                NodeConfiguration config = configMap.get(elementId);
                if (config == null) {
                    throw new BusinessException(
                            "UserTask '" + elementId + "' no tiene NodeConfiguration");
                }
                if (!"CLIENT_TASK".equals(config.getTaskKind())) {
                    throw new BusinessException(
                            "P3.4.1 solo soporta CLIENT_TASK. Nodo: " + elementId
                            + " (taskKind=" + config.getTaskKind() + ")");
                }
                yield NodeType.CLIENT_TASK;
            }
            case "serviceTask", "sendTask", "exclusiveGateway", "parallelGateway",
                 "inclusiveGateway", "complexGateway", "task", "subProcess", "callActivity" ->
                throw new BusinessException(
                        "Nodo '" + localName + "' (" + elementId + ") no soportado en P3.4.1");

            default -> null; // Diagram interchange elements (BPMNDiagram, etc.)
        };
    }

    private String getName(Element el) {
        String name = el.getAttribute("name");
        if (name != null && !name.isBlank()) return name;
        // Fallback: try to get name from child element (some BPMN variants)
        var children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childName = ((Element) child).getAttribute("name");
                if (childName != null && !childName.isBlank()) return childName;
            }
        }
        return "";
    }
}
