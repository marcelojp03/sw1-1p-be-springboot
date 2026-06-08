package sw1.p1.document.domain;

public enum DocumentPermission {
    DOCUMENT_VIEW,      // Ver metadata y previsualizar/descargar
    DOCUMENT_UPLOAD,    // Subir documentos nuevos
    DOCUMENT_REPLACE,   // Crear una nueva versión (reemplazar)
    DOCUMENT_APPROVE,   // Aprobar o marcar como observado
    DOCUMENT_COMMENT,   // Agregar comentarios
    DOCUMENT_EDIT,      // Abrir en OnlyOffice para edición colaborativa
    DOCUMENT_SIGN,      // Enviar al servicio externo de firma
    DOCUMENT_DELETE     // Eliminar o archivar lógicamente
}
