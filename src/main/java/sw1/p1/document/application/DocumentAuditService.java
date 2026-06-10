package sw1.p1.document.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import sw1.p1.document.domain.AuditAction;
import sw1.p1.document.dto.AuditLogResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Escribe entradas de auditoría documental en DynamoDB (append-only).
 * Tabla: ${DYNAMODB_TABLE_PREFIX}_document_audit_logs
 * PK: documentId  |  SK: timestamp (ISO 8601)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAuditService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${dynamodb.table-prefix:sw1}")
    private String tablePrefix;

    public List<AuditLogResponse> queryByDocumentId(String documentId) {
        String tableName = tablePrefix + "_document_audit_logs";

        Map<String, AttributeValue> eav = Map.of(":docId", attr(documentId));

        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("documentId = :docId")
                .expressionAttributeValues(eav)
                .scanIndexForward(false)
                .build();

        try {
            QueryResponse response = dynamoDbClient.query(request);
            List<AuditLogResponse> logs = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                logs.add(AuditLogResponse.fromDynamoItem(item));
            }
            return logs;
        } catch (Exception e) {
            log.error("Error querying audit logs for document {}: {}", documentId, e.getMessage());
            return List.of();
        }
    }

    public void log(String documentId, String versionId, AuditAction action,
                    String performedBy, String detail) {
        String tableName = tablePrefix + "_document_audit_logs";
        String timestamp  = Instant.now().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("documentId",  attr(documentId));
        item.put("timestamp",   attr(timestamp));
        item.put("auditId",     attr(UUID.randomUUID().toString()));
        item.put("action",      attr(action.name()));
        item.put("performedBy", attr(performedBy != null ? performedBy : "system"));
        if (versionId != null) item.put("versionId", attr(versionId));
        if (detail    != null) item.put("detail",    attr(detail));

        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());
        } catch (Exception e) {
            log.error("Error writing audit log for document {} action {}: {}", documentId, action, e.getMessage());
        }
    }

    private AttributeValue attr(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
