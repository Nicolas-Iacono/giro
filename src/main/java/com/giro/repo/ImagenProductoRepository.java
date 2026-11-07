package com.giro.repo;

import com.giro.domain.ImagenProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImagenProductoRepository extends JpaRepository<ImagenProducto, Long> {
    List<ImagenProducto> findByProductoIdOrderByOrdenAsc(Long productoId);
    Optional<ImagenProducto> findByIdAndProductoId(Long id, Long productoId);
    int countByProductoId(Long productoId);
    void deleteByProductoId(Long productoId);

    @Query("select i from ImagenProducto i where i.producto.id in :ids order by i.producto.id asc, i.orden asc")
    List<ImagenProducto> deProductos(@Param("ids") Collection<Long> ids);
}
