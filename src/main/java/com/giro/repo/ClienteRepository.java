package com.giro.repo;

import com.giro.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByComercioIdAndActivoTrueOrderByNombreAsc(Long comercioId);
    Optional<Cliente> findByIdAndComercioId(Long id, Long comercioId);
    List<Cliente> findByComercioIdAndActivoTrueAndSaldoGreaterThanOrderByNombreAsc(Long comercioId, java.math.BigDecimal saldo);
}
