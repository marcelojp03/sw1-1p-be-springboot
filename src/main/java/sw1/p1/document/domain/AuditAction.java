package sw1.p1.document.domain;

public enum AuditAction {
    UPLOADED,
    VIEWED,
    DOWNLOADED,
    REPLACED,
    APPROVED,
    OBSERVED,
    COMMENTED,
    DELETED,
    CONVERTED,       // PDF → DOCX vía FastAPI
    EDIT_STARTED,    // Usuario abrió OnlyOffice
    EDIT_SAVED,      // OnlyOffice callback → nueva versión guardada
    SIGNED
}
