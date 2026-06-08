package sw1.p1.document.domain;

public enum DocumentStatus {
    ACTIVE,     // Versión vigente visible
    REPLACED,   // Superada por una versión más nueva
    OBSERVED,   // Marcada con observaciones por revisor
    APPROVED,   // Aprobada formalmente
    ARCHIVED,   // Archivada lógicamente (no eliminada físicamente)
    DELETED     // Eliminación lógica
}
