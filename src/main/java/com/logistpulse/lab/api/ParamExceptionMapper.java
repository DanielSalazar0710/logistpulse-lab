package com.logistpulse.lab.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.glassfish.jersey.server.ParamException;

/**
 * JAX-RS responde 404 cuando un @QueryParam no se puede convertir (p. ej. ?status=FOO), lo que confunde al cliente:
 * el recurso sí existe, lo que está mal es el parámetro. Lo traducimos a 400 con un mensaje claro.
 * (Los @PathParam inválidos siguen siendo 404, que es lo correcto: esa URI no identifica ningún recurso.)
 */
@Provider
public class ParamExceptionMapper implements ExceptionMapper<ParamException> {

    @Override
    public Response toResponse(ParamException e) {
        int status = e instanceof ParamException.PathParamException ? 404 : 400;
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("status", status,
                        "error", "Valor inválido para el parámetro '" + e.getParameterName() + "'"))
                .build();
    }
}
