"""
AWS Lambda: S3 ObjectCreated → DynamoDB audit log
Trigger: S3 bucket sw1-2p-documents, eventos s3:ObjectCreated:*
Tabla DynamoDB: sw1_document_audit_logs  (PK=documentId, SK=timestamp)

Convención de S3 key: org/{orgId}/doc/{docId}/v{n}/{filename}
"""

import os
import boto3
from datetime import datetime, timezone

TABLE_NAME = os.environ.get("DYNAMODB_TABLE", "sw1_document_audit_logs")

dynamodb = boto3.resource("dynamodb")
table = dynamodb.Table(TABLE_NAME)


def lambda_handler(event, context):
    for record in event.get("Records", []):
        s3 = record["s3"]
        bucket = s3["bucket"]["name"]
        key = s3["object"]["key"]
        size = s3["object"].get("size", 0)
        event_time = record.get("eventTime", datetime.now(timezone.utc).isoformat())

        # Extraer documentId del key: org/{orgId}/doc/{docId}/...
        parts = key.split("/")
        doc_id = parts[3] if len(parts) > 3 else key
        org_id = parts[1] if len(parts) > 1 else "unknown"

        table.put_item(Item={
            "documentId": doc_id,
            "timestamp": event_time,
            "organizationId": org_id,
            "bucket": bucket,
            "s3Key": key,
            "sizeBytes": size,
            "eventType": "DOCUMENT_UPLOADED",
        })

    return {"statusCode": 200}
