package com.giro.repo;

import com.giro.domain.MovimientoCaja;
import com.giro.domain.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
    List<MovimientoCaja> findByCajaIdOrderByCreadoEnAsc(Long cajaId);

    @Query("""
            select m from MovimientoCaja m
            left join fetch m.venta
            left join fetch m.cliente
            where m.comercio.id = :comercioId
            """)
    List<MovimientoCaja> delComercio(Long comercioId);

    @Query("select coalesce(sum(m.monto), 0) from MovimientoCaja m where m.caja.id = :cajaId and m.tipo = :tipo")
    BigDecimal sumar(Long cajaId, TipoMovimientoCaja tipo);

    @Query("""
            select coalesce(sum(m.monto), 0) from MovimientoCaja m
            where m.comercio.id = :comercioId and m.tipo = :tipo and m.venta is null
              and m.creadoEn >= :desde and m.creadoEn < :hasta
            """)
    BigDecimal gastosManuales(Long comercioId, TipoMovimientoCaja tipo, LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select m from MovimientoCaja m
            where m.comercio.id = :comercioId and m.tipo = :tipo and m.venta is null
              and m.creadoEn >= :desde and m.creadoEn < :hasta
            order by m.creadoEn desc
            """)
    List<MovimientoCaja> manuales(Long comercioId, TipoMovimientoCaja tipo, LocalDateTime desde, LocalDateTime hasta);
}
