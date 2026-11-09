package com.giro.repo;

import com.giro.domain.WhatsappMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappMensajeRepository extends JpaRepository<WhatsappMensaje, Long> {
    List<WhatsappMensaje> findByContactoIdOrderByCreadoEnAscIdAsc(Long contactoId);
    Optional<WhatsappMensaje> findByMetaId(String metaId);
}
