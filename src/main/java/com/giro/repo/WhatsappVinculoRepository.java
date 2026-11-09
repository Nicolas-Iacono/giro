package com.giro.repo;

import com.giro.domain.WhatsappVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsappVinculoRepository extends JpaRepository<WhatsappVinculo, Long> {
    Optional<WhatsappVinculo> findByClave(String clave);
}
