package sw1.p1.form.application;

import org.springframework.stereotype.Service;
import sw1.p1.form.domain.*;
import sw1.p1.form.exception.FormValidationException;

import java.util.*;

@Service
public class FormValidationService {

    public List<String> collectErrors(List<FormFieldDefinition> fields) {
        List<String> errors = new ArrayList<>();

        if (fields == null || fields.isEmpty()) {
            errors.add("El formulario debe tener al menos un campo");
            return errors;
        }

        Set<String> keys = new HashSet<>();
        Set<String> ids = new HashSet<>();

        for (FormFieldDefinition field : fields) {
            if (field.getId() == null || field.getId().isBlank()) {
                errors.add("Cada campo debe tener un id");
            }
            if (field.getKey() == null || field.getKey().isBlank()) {
                errors.add("Cada campo debe tener una key");
            }
            if (field.getType() == null) {
                errors.add("El campo '" + field.getKey() + "' no tiene tipo");
            }
            if (field.getId() != null && !ids.add(field.getId())) {
                errors.add("Id duplicado: " + field.getId());
            }
            if (field.getKey() != null && !keys.add(field.getKey())) {
                errors.add("Key duplicada: " + field.getKey());
            }
            if (field.getLabel() == null || field.getLabel().isBlank()) {
                errors.add("El campo '" + field.getKey() + "' no tiene label");
            }

            if (field.getType() != null) {
                collectFieldErrors(field, errors);
            }
        }

        return errors;
    }

    private void collectFieldErrors(FormFieldDefinition field, List<String> errors) {
        FormFieldType type = field.getType();
        String key = field.getKey();

        switch (type) {
            case SELECT, RADIO -> {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    errors.add("El campo '" + key + "' de tipo " + type + " requiere opciones");
                }
                validateOptions(field.getOptions(), key, errors);
            }
            case CHECKLIST -> {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    errors.add("El campo '" + key + "' de tipo CHECKLIST requiere opciones");
                }
                validateOptions(field.getOptions(), key, errors);
            }
            case GRID -> {
                if (field.getColumns() == null || field.getColumns().isEmpty()) {
                    errors.add("El campo '" + key + "' de tipo GRID requiere columnas");
                } else {
                    Set<String> colKeys = new HashSet<>();
                    for (GridColumnDefinition col : field.getColumns()) {
                        if (col.getKey() == null || col.getKey().isBlank()) {
                            errors.add("Columna sin key en GRID '" + key + "'");
                        }
                        if (col.getLabel() == null || col.getLabel().isBlank()) {
                            errors.add("Columna sin label en GRID '" + key + "'");
                        }
                        if (col.getType() == null) {
                            errors.add("Columna '" + col.getKey() + "' sin tipo en GRID '" + key + "'");
                        }
                        if (col.getKey() != null && !colKeys.add(col.getKey())) {
                            errors.add("Columna duplicada '" + col.getKey() + "' en GRID '" + key + "'");
                        }
                        if (col.getType() == GridColumnType.SELECT && (col.getOptions() == null || col.getOptions().isEmpty())) {
                            errors.add("Columna SELECT '" + col.getKey() + "' requiere opciones");
                        }
                        if (col.getOptions() != null) {
                            validateOptions(col.getOptions(), "columna " + col.getKey(), errors);
                        }
                    }
                }
            }
        }

        if (field.getValidation() != null) {
            var v = field.getValidation();
            if (v.getMinLength() != null && v.getMinLength() < 0)
                errors.add("minLength negativo en campo '" + key + "'");
            if (v.getMaxLength() != null && v.getMaxLength() < 0)
                errors.add("maxLength negativo en campo '" + key + "'");
            if (v.getMinLength() != null && v.getMaxLength() != null && v.getMinLength() > v.getMaxLength())
                errors.add("minLength > maxLength en campo '" + key + "'");
            if (v.getMin() != null && v.getMax() != null && v.getMin().compareTo(v.getMax()) > 0)
                errors.add("min > max en campo '" + key + "'");
            if (v.getMinItems() != null && v.getMinItems() < 0)
                errors.add("minItems negativo en campo '" + key + "'");
            if (v.getMaxItems() != null && v.getMaxItems() < 0)
                errors.add("maxItems negativo en campo '" + key + "'");
            if (v.getMinItems() != null && v.getMaxItems() != null && v.getMinItems() > v.getMaxItems())
                errors.add("minItems > maxItems en campo '" + key + "'");
            if (v.getRegex() != null && !v.getRegex().isBlank()) {
                try {
                    java.util.regex.Pattern.compile(v.getRegex());
                } catch (Exception e) {
                    errors.add("Regex invalido en campo '" + key + "': " + e.getMessage());
                }
            }
        }
    }

    private void validateOptions(List<FormOption> options, String context, List<String> errors) {
        Set<String> values = new HashSet<>();
        for (var opt : options) {
            if (opt.getValue() == null || opt.getValue().isBlank()) {
                errors.add("Opcion sin value en " + context);
            }
            if (opt.getLabel() == null || opt.getLabel().isBlank()) {
                errors.add("Opcion sin label en " + context);
            }
            if (opt.getValue() != null && !values.add(opt.getValue())) {
                errors.add("Opcion duplicada '" + opt.getValue() + "' en " + context);
            }
        }
    }
}
