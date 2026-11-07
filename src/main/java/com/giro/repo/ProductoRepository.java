package com.giro.repo;

import com.giro.domain.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByComercioIdOrderByNombreAsc(Long comercioId);
    List<Producto> findByComercioIdAndActivoTrueAndPublicadoTrueOrderByNombreAsc(Long comercioId);
    Optional<Producto> findByIdAndComercioId(Long id, Long comercioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id and p.comercio.id = :comercioId")
    Optional<Producto> lock(@Param("id") Long id, @Param("comercioId") Long comercioId);

    long countByComercioIdAndActivoTrue(Long comercioId);
    boolean existsByComercioIdAndSkuAndIdNot(Long comercioId, String sku, Long id);
    boolean existsByComercioIdAndSku(Long comercioId, String sku);

    @Query("""
            select p from Producto p
            where p.comercio.id = :comercioId and p.activo = true and p.usaVariantes = false
              and p.stock <= p.stockMinimo
            """)
    List<Producto> stockBajo(@Param("comercioId") Long comercioId);
}
