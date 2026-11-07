package com.giro.servicio;

import com.giro.domain.Plan;
import com.giro.repo.PlanRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class PlanInicial {
    @Bean
    ApplicationRunner sembrarPlan(PlanRepository planes) {
        return args -> {
            if (planes.findByCodigo("INICIO").isPresent()) return;
            Plan plan = new Plan();
            plan.setCodigo("INICIO");
            plan.setNombre("Inicio");
            plan.setPrecioMensual(BigDecimal.ZERO);
            plan.setMaxProductos(200);
            plan.setMaxEmpleados(5);
            planes.save(plan);
        };
    }
}
