package com.giro.repo;

import com.giro.domain.EstadoVenta;
import com.giro.domain.MedioPago;
import com.giro.domain.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    Optional<Venta> findByIdAndComercioId(Long id, Long comercioId);

    @Query("""
            select distinct v from Venta v
            left join fetch v.detalles d
            left join fetch d.producto
            left join fetch d.variante
            left join fetch v.cliente
            where v.comercio.id = :comercioId
            """)
    List<Venta> historial(Long comercioId);
    List<Venta> findTop50ByComercioIdOrderByCreadaEnDesc(Long comercioId);
    List<Venta> findByComercioIdAndClienteIdOrderByCreadaEnDesc(Long comercioId, Long clienteId);

    @Query("""
            select coalesce(sum(v.total), 0) from Venta v
            where v.comercio.id = :comercioId and v.estado = :estado
              and v.creadaEn >= :desde and v.creadaEn < :hasta
            """)
    BigDecimal sumarVentas(Long comercioId, EstadoVenta estado, LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select coalesce(sum(v.total - v.costoTotal), 0) from Venta v
            where v.comercio.id = :comercioId and v.estado = :estado
              and v.creadaEn >= :desde and v.creadaEn < :hasta
            """)
    BigDecimal sumarGanancia(Long comercioId, EstadoVenta estado, LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select coalesce(sum(p.monto), 0) from Pago p
            where p.venta.comercio.id = :comercioId and p.venta.estado = :estado and p.medio <> :fiado
              and p.venta.creadaEn >= :desde and p.venta.creadaEn < :hasta
            """)
    BigDecimal sumarCobros(Long comercioId, EstadoVenta estado, MedioPago fiado, LocalDateTime desde, LocalDateTime hasta);

    List<Venta> findByComercioIdAndEstadoAndCreadaEnGreaterThanEqualOrderByCreadaEnAsc(
            Long comercioId, EstadoVenta estado, LocalDateTime desde);

    @Query("""
            select distinct v from Venta v
            left join fetch v.detalles d
            left join fetch d.producto
            left join fetch d.variante
            left join fetch v.pedido
            left join fetch v.cliente
            where v.comercio.id = :comercioId and v.estado = :estado
              and v.creadaEn >= :desde and v.creadaEn < :hasta
            """)
    List<Venta> delPeriodo(Long comercioId, EstadoVenta estado, LocalDateTime desde, LocalDateTime hasta);
}
