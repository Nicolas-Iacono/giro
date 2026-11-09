package com.giro.repo;

import com.giro.domain.WhatsappVinculo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsappVinculoRepository extends JpaRepository<WhatsappVinculo, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<WhatsappVinculo> findByClave(String clave);
}
