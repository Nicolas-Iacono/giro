package com.giro.repo;

import com.giro.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByComercioIdOrderByNombreAsc(Long comercioId);
    Optional<Categoria> findByIdAndComercioId(Long id, Long comercioId);
}
