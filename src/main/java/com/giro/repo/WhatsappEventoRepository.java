package com.giro.repo;

import com.giro.domain.WhatsappEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WhatsappEventoRepository extends JpaRepository<WhatsappEvento, Long> {
    Optional<WhatsappEvento> findByDedupe(String dedupe);
    List<WhatsappEvento> findTop20ByEstadoOrderByIdAsc(String estado);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WhatsappEvento e set e.estado = 'PROCESANDO' where e.id = :id and e.estado = 'PENDIENTE'")
    int reclamar(@Param("id") Long id);
}
