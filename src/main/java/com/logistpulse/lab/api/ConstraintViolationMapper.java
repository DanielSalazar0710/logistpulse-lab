package com.logistpulse.lab.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parte 1.3: errores de Bean Validation (@Valid en los recursos) → 400 con la lista de campos inválidos.
 * Sin este mapper Jersey usa su ValidationExceptionMapper, que responde 400 sin cuerpo útil para el cliente.
 */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException e) {
        List<Map<String, Object>> fields = e.getConstraintViolations().stream()
                .sorted(Comparator.comparing(v -> fieldName(v)))
                .map(v -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("field", fieldName(v));
                    f.put("message", v.getMessage());
                    f.put("rejected", v.getInvalidValue());
                    return f;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Datos de entrada inválidos");
        body.put("fields", fields);
        return Response.status(400).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    /** La ruta llega como "create.arg0.customer": al cliente solo le interesa el último nodo. */
    private static String fieldName(ConstraintViolation<?> v) {
        String name = null;
        for (Path.Node node : v.getPropertyPath()) {
            name = node.getName();
        }
        return name == null ? v.getPropertyPath().toString() : name;
    }
}
