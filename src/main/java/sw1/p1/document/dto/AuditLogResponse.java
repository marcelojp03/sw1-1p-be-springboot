package sw1.p1.document.dto;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public record AuditLogResponse(
        String documentId,
        String timestamp,
        String auditId,
        String action,
        String performedBy,
        String versionId,
        String detail
) {
    public static AuditLogResponse fromDynamoItem(Map<String, AttributeValue> item) {
        return new AuditLogResponse(
                getS(item, "documentId"),
                getS(item, "timestamp"),
                getS(item, "auditId"),
                getS(item, "action"),
                getS(item, "performedBy"),
                getS(item, "versionId"),
                getS(item, "detail")
        );
    }

    private static String getS(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }
}
