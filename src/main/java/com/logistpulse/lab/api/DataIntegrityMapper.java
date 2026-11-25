package com.logistpulse.lab.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Una restricción de la base (CHECK, UNIQUE, FK) rechazó la operación: es un conflicto con el estado actual,
 * no un error interno. Respondemos 409 en JSON en lugar de la página HTML 500 de Tomcat.
 */
@Provider
public class DataIntegrityMapper implements ExceptionMapper<DataIntegrityViolationException> {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityMapper.class);

    @Override
    public Response toResponse(DataIntegrityViolationException e) {
        log.warn("Restricción de integridad violada: {}", e.getMostSpecificCause().getMessage());
        return Response.status(409)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", 409, "error", "La operación viola una regla de integridad del sistema"))
                .build();
    }
}
