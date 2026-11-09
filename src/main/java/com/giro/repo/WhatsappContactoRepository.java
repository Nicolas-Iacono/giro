package com.giro.repo;

import com.giro.domain.WhatsappContacto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsappContactoRepository extends JpaRepository<WhatsappContacto, Long> {
    List<WhatsappContacto> findByComercioIdOrderByUltimoEnDesc(Long comercioId);
    Optional<WhatsappContacto> findByIdAndComercioId(Long id, Long comercioId);
    Optional<WhatsappContacto> findByComercioIdAndTelefono(Long comercioId, String telefono);
}
