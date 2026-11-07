package com.giro.repo;

import com.giro.domain.MovimientoStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {
}
