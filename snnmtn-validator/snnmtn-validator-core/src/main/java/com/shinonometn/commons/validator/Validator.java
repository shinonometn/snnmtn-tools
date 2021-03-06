package com.shinonometn.commons.validator;

import com.shinonometn.commons.validator.exception.ValidationException;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class Validator {

    private final ValidationMeta validationMeta;

    public Validator(ValidationMeta validationMeta) {
        this.validationMeta = validationMeta;

        validationMeta
                .getGroup()
                .stream()
                .filter(name -> name.matches("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*"))
                .map(name -> {
                    try {
                        return Introspector.getBeanInfo(Class.forName(name));
                    } catch (IntrospectionException | ClassNotFoundException e) {
                        throw new RuntimeException("Could not get class " + name);
                    }
                })
                .collect(toMap(
                        c -> c.getBeanDescriptor().getBeanClass().getName(),
                        c -> c))
                .forEach((name, value) -> validationMeta.getFields(name).forEach(fieldValidator -> {
                    try {
                        fieldValidator.setPropertyDescriptor(new PropertyDescriptor(fieldValidator.getField(), Class.forName(name)));
                    } catch (IntrospectionException | ClassNotFoundException exception) {
                        throw new IllegalStateException(
                                String.format("Could not create field validator for field %s@%s", name, fieldValidator.getField()),
                                exception);
                    }
                }));
    }

    public void validate(Object form) {
        String group = form.getClass().getName();
        validate(group, form);
    }

    public void validate(String group, Object form) {
        List<FieldValidator> validators = validationMeta.getFields(group);
        if (validators == null) {
//            logger.info("Field validator for {} not registered, skipped.", group);
            return;
        }

        Map<String, Collection<String>> map = new HashMap<>();
        for (FieldValidator validator : validators) {
            String fieldName = validator.getField();
            if (!validator.validate(form)) {
                if (!map.containsKey(fieldName))
                    map.put(fieldName, new HashSet<>());

                map.get(fieldName).add(validator.getMessage());
            }
        }

        ValidateResult result = new ValidateResult(map);

        if (result.hasError()) throw new ValidationException(result);
    }
}
