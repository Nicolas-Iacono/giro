package com.giro.error;

import org.springframework.http.HttpStatus;

public class TokenRevocadoException extends NegocioException {
    public TokenRevocadoException() {
        super(HttpStatus.UNAUTHORIZED, "Meta revocó el acceso de WhatsApp. El número sigue en el celular: volvé a conectarlo.");
    }
}
