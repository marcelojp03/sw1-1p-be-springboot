package sw1.p1.document.domain;

/** Nivel de asociación del documento dentro del workflow */
public enum DocumentScope {
    POLICY,     // Repositorio general de la política
    PROCEDURE,  // Documento específico de una instancia de trámite
    NODE,       // Documento requerido en un nodo/etapa específico
    FORM,       // Adjunto de un campo FILE en un formulario
    TASK        // Archivo entregado para completar una CLIENT_TASK
}
