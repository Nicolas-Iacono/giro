package com.giro.seguridad;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.giro.domain.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Algorithm algorithm;

    public JwtService(@Value("${giro.jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public String emitir(Usuario usuario) {
        return JWT.create()
                .withSubject(usuario.getId().toString())
                .withClaim("rol", usuario.getRol().name())
                .sign(algorithm);
    }

    public Long leerUsuarioId(String token) {
        DecodedJWT jwt = JWT.decode(token);
        algorithm.verify(jwt);
        return Long.valueOf(jwt.getSubject());
    }
}
