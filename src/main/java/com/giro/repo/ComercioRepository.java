package com.giro.repo;

import com.giro.domain.Comercio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComercioRepository extends JpaRepository<Comercio, Long> {
    boolean existsBySlug(String slug);
    Optional<Comercio> findBySlug(String slug);
}
