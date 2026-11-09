package com.giro.servicio;

import com.giro.domain.WhatsappEvento;
import com.giro.repo.WhatsappEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class WhatsappCola {
    private static final Logger log = LoggerFactory.getLogger(WhatsappCola.class);
    private final WhatsappEventoRepository eventos;
    private final WhatsappGiroService whatsapp;
    private final TransactionTemplate transacciones;

    public WhatsappCola(WhatsappEventoRepository eventos, WhatsappGiroService whatsapp, PlatformTransactionManager transacciones) {
        this.eventos = eventos;
        this.whatsapp = whatsapp;
        this.transacciones = new TransactionTemplate(transacciones);
    }

    @Transactional
    public boolean aceptar(byte[] cuerpo, String firma) {
        if (!whatsapp.firmaValida(cuerpo, firma)) return false;
        String dedupe = huella(cuerpo);
        if (eventos.findByDedupe(dedupe).isPresent()) return true;
        WhatsappEvento evento = new WhatsappEvento();
        evento.setDedupe(dedupe);
        evento.setCuerpo(new String(cuerpo, StandardCharsets.UTF_8));
        evento.setEstado("PENDIENTE");
        evento.setCreadoEn(LocalDateTime.now());
        eventos.save(evento);
        return true;
    }

    @Scheduled(fixedDelay = 3000)
    public void drenar() {
        for (Long id : eventos.findTop20ByEstadoOrderByIdAsc("PENDIENTE").stream().map(WhatsappEvento::getId).toList()) {
            try {
                transacciones.executeWithoutResult(estado -> procesar(id));
            } catch (RuntimeException ex) {
                transacciones.executeWithoutResult(estado -> fallar(id, ex));
            }
        }
    }

    private void procesar(Long id) {
        if (eventos.reclamar(id) == 0) return;
        WhatsappEvento evento = eventos.findById(id).orElseThrow();
        whatsapp.recibir(evento.getCuerpo());
        evento.setEstado("HECHO");
        evento.setProcesadoEn(LocalDateTime.now());
        evento.setError(null);
        eventos.save(evento);
    }

    private void fallar(Long id, Exception ex) {
        eventos.findById(id).ifPresent(evento -> {
            evento.setIntentos(evento.getIntentos() + 1);
            evento.setEstado(evento.getIntentos() >= 5 ? "ERROR" : "PENDIENTE");
            evento.setError(ex.getClass().getSimpleName());
            log.warn("Webhook de WhatsApp sin procesar: {}", ex.getClass().getSimpleName());
        });
    }

    private String huella(byte[] cuerpo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cuerpo));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
