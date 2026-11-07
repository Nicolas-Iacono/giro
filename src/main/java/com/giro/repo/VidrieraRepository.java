package com.giro.repo;

import com.giro.domain.Vidriera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VidrieraRepository extends JpaRepository<Vidriera, Long> {
    Optional<Vidriera> findByComercioId(Long comercioId);
    Optional<Vidriera> findByComercioSlug(String slug);
}
