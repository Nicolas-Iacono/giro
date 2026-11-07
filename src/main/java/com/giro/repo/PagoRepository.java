package com.giro.repo;

import com.giro.domain.EstadoVenta;
import com.giro.domain.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByVentaId(Long ventaId);

    @Query("select p from Pago p where p.venta.comercio.id = :comercioId")
    List<Pago> delComercio(Long comercioId);

    @Query("""
            select p from Pago p
            where p.venta.comercio.id = :comercioId and p.venta.estado = :estado
              and p.venta.creadaEn >= :desde and p.venta.creadaEn < :hasta
            """)
    List<Pago> delPeriodo(@Param("comercioId") Long comercioId, @Param("estado") EstadoVenta estado,
                          @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
