package com.giro.repo;

import com.giro.domain.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @EntityGraph(attributePaths = "comercio")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "comercio")
    Optional<Usuario> findWithComercioById(Long id);

    long countByComercioIdAndActivoTrue(Long comercioId);

    List<Usuario> findByComercioIdOrderByNombreAsc(Long comercioId);

    boolean existsByEmailIgnoreCase(String email);
}
