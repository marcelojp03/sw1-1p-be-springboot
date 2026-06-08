package sw1.p1.document.domain;

public enum VersionType {
    ORIGINAL,   // Archivo subido por el usuario tal como está
    EDITABLE,   // Convertido a DOCX por FastAPI (para OnlyOffice)
    SIGNED      // Versión firmada electrónicamente (final)
}
