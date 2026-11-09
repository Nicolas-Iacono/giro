package com.giro.servicio;

import com.giro.domain.Comercio;
import com.giro.repo.ComercioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CajaTurnoJob {
    private static final Logger log = LoggerFactory.getLogger(CajaTurnoJob.class);

    private final ComercioRepository comercios;
    private final ComercialService comercial;

    public CajaTurnoJob(ComercioRepository comercios, ComercialService comercial) {
        this.comercios = comercios;
        this.comercial = comercial;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Argentina/Buenos_Aires")
    public void medianoche() {
        sincronizar();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        sincronizar();
    }

    private void sincronizar() {
        for (Comercio comercio : comercios.findAll()) {
            try {
                comercial.sincronizarComercio(comercio);
            } catch (RuntimeException ex) {
                log.warn("No se pudo sincronizar la caja del comercio {}", comercio.getId(), ex);
            }
        }
    }
}
