package com.giro.repo;

import com.giro.domain.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    boolean existsByProductoId(Long productoId);
}
