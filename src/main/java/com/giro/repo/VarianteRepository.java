package com.giro.repo;

import com.giro.domain.Variante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VarianteRepository extends JpaRepository<Variante, Long> {
    List<Variante> findByProductoIdAndActivoTrueOrderByNombreAsc(Long productoId);
    void deleteByProductoId(Long productoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variante v where v.id = :id and v.producto.id = :productoId")
    Optional<Variante> lock(@Param("id") Long id, @Param("productoId") Long productoId);

    boolean existsByProducto_Comercio_IdAndSku(Long comercioId, String sku);

    @Query("""
            select v from Variante v
            where v.producto.comercio.id = :comercioId and v.activo = true and v.producto.activo = true
              and v.stock <= v.producto.stockMinimo
            """)
    List<Variante> stockBajo(@Param("comercioId") Long comercioId);
}
