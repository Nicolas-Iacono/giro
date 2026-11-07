package com.giro.repo;

import com.giro.domain.Caja;
import com.giro.domain.EstadoCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Long> {
    Optional<Caja> findByComercioIdAndEstado(Long comercioId, EstadoCaja estado);
    java.util.List<Caja> findTop90ByComercioIdAndEstadoOrderByCerradaEnDesc(Long comercioId, EstadoCaja estado);
    Optional<Caja> findFirstByComercioIdOrderByAbiertaEnDesc(Long comercioId);
}
