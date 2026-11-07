package com.giro.seguridad;

import com.giro.domain.RolComercio;
import com.giro.domain.Usuario;
import com.giro.error.NegocioException;
import com.giro.repo.SuscripcionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class Sesion {
    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final SuscripcionRepository suscripciones;

    public Sesion(SuscripcionRepository suscripciones) {
        this.suscripciones = suscripciones;
    }

    public Usuario usuario() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new NegocioException(HttpStatus.UNAUTHORIZED, "Tenés que iniciar sesión");
        }
        return usuario;
    }

    public Long comercioId() {
        return usuario().getComercio().getId();
    }

    public void exigirGestion() {
        RolComercio rol = usuario().getRol();
        if (rol != RolComercio.DUENO && rol != RolComercio.ADMINISTRADOR) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tenés permiso para administrar el comercio");
        }
    }

    public void exigirCaja() {
        RolComercio rol = usuario().getRol();
        if (rol == RolComercio.EMPLEADO) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "La caja la opera un cajero, un administrador o el dueño");
        }
    }

    public void exigirSuscripcion() {
        var suscripcion = suscripciones.findByComercioId(comercioId())
                .orElseThrow(() -> new NegocioException("El comercio no tiene una suscripción"));
        if (suscripcion.getVenceEn().isBefore(LocalDate.now(ZONA))) {
            suscripcion.setEstado(com.giro.domain.EstadoSuscripcion.VENCIDA);
            throw new NegocioException(HttpStatus.PAYMENT_REQUIRED, "La suscripción está vencida");
        }
    }
}
