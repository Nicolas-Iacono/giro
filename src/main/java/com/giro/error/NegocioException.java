package com.giro.error;

import org.springframework.http.HttpStatus;

public class NegocioException extends RuntimeException {
    private final HttpStatus status;

    public NegocioException(String mensaje) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
    }

    public NegocioException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
