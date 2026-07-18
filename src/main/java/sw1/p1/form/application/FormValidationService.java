package sw1.p1.form.application;

import org.springframework.stereotype.Service;
import sw1.p1.form.domain.FormFieldDefinition;
import sw1.p1.form.domain.FormFieldType;
import sw1.p1.form.domain.GridColumnDefinition;
import sw1.p1.form.domain.GridColumnType;
import sw1.p1.form.exception.FormValidationException;

import java.util.*;

@Service
public class FormValidationService {

    public void validate(List<FormFieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new FormValidationException("El formulario debe tener al menos un campo");
        }

        Set<String> keys = new HashSet<>();
        Set<String> ids = new HashSet<>();

        for (FormFieldDefinition field : fields) {
            if (field.getId() == null || field.getId().isBlank()) {
                throw new FormValidationException("Cada campo debe tener un id");
            }
            if (field.getKey() == null || field.getKey().isBlank()) {
                throw new FormValidationException("Cada campo debe tener una key");
            }
            if (field.getType() == null) {
                throw new FormValidationException("El campo '" + field.getKey() + "' no tiene tipo");
            }

            if (!ids.add(field.getId())) {
                throw new FormValidationException("Id duplicado: " + field.getId());
            }
            if (!keys.add(field.getKey())) {
                throw new FormValidationException("Key duplicada: " + field.getKey());
            }

            validateField(field);
        }
    }

    private void validateField(FormFieldDefinition field) {
        FormFieldType type = field.getType();

        switch (type) {
            case SELECT, RADIO -> {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    throw new FormValidationException(
                            "El campo '" + field.getKey() + "' de tipo " + type + " requiere opciones");
                }
                Set<String> optionValues = new HashSet<>();
                for (var opt : field.getOptions()) {
                    if (opt.getValue() == null || opt.getValue().isBlank()) {
                        throw new FormValidationException(
                                "Opcion sin value en campo '" + field.getKey() + "'");
                    }
                    if (!optionValues.add(opt.getValue())) {
                        throw new FormValidationException(
                                "Opcion duplicada '" + opt.getValue() + "' en campo '" + field.getKey() + "'");
                    }
                }
            }
            case CHECKLIST -> {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    throw new FormValidationException(
                            "El campo '" + field.getKey() + "' de tipo CHECKLIST requiere opciones");
                }
            }
            case GRID -> {
                if (field.getColumns() == null || field.getColumns().isEmpty()) {
                    throw new FormValidationException(
                            "El campo '" + field.getKey() + "' de tipo GRID requiere columnas");
                }
                Set<String> colKeys = new HashSet<>();
                for (GridColumnDefinition col : field.getColumns()) {
                    if (col.getKey() == null || col.getKey().isBlank()) {
                        throw new FormValidationException(
                                "Columna sin key en GRID '" + field.getKey() + "'");
                    }
                    if (col.getType() == null) {
                        throw new FormValidationException(
                                "Columna '" + col.getKey() + "' sin tipo en GRID '" + field.getKey() + "'");
                    }
                    if (!colKeys.add(col.getKey())) {
                        throw new FormValidationException(
                                "Columna duplicada '" + col.getKey() + "' en GRID '" + field.getKey() + "'");
                    }
                    if (col.getType() == GridColumnType.SELECT && (col.getOptions() == null || col.getOptions().isEmpty())) {
                        throw new FormValidationException(
                                "Columna SELECT '" + col.getKey() + "' requiere opciones en GRID '" + field.getKey() + "'");
                    }
                }
            }
        }

        if (field.getValidation() != null) {
            var v = field.getValidation();
            if (v.getMinLength() != null && v.getMaxLength() != null && v.getMinLength() > v.getMaxLength()) {
                throw new FormValidationException(
                        "minLength > maxLength en campo '" + field.getKey() + "'");
            }
            if (v.getMin() != null && v.getMax() != null && v.getMin().compareTo(v.getMax()) > 0) {
                throw new FormValidationException(
                        "min > max en campo '" + field.getKey() + "'");
            }
            if (v.getMinItems() != null && v.getMaxItems() != null && v.getMinItems() > v.getMaxItems()) {
                throw new FormValidationException(
                        "minItems > maxItems en campo '" + field.getKey() + "'");
            }
        }
    }
}
