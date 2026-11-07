package com.giro.repo;

import com.giro.domain.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByComercioId(Long comercioId);
}
