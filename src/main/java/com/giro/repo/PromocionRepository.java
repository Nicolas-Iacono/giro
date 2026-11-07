package com.giro.repo;

import com.giro.domain.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PromocionRepository extends JpaRepository<Promocion, Long> {
    void deleteByProductoId(Long productoId);
    @Query("""
            select p from Promocion p
            where p.comercio.id = :comercioId and p.producto.id = :productoId and p.activa = true
              and (p.desde is null or p.desde <= :hoy)
              and (p.hasta is null or p.hasta >= :hoy)
            order by p.precioPromo asc
            """)
    List<Promocion> vigentes(@Param("comercioId") Long comercioId, @Param("productoId") Long productoId, @Param("hoy") LocalDate hoy);
}
