package com.giro.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giro.domain.*;
import com.giro.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsappCoexistenciaEventosTest {
    WhatsappGiroService service;
    Comercio a;
    WhatsappContacto contacto;
    WhatsappMensajeRepository mensajes;
    Map<String, WhatsappMensaje> guardados;

    @BeforeEach void setup() {
        var comercios = mock(ComercioRepository.class);
        var contactos = mock(WhatsappContactoRepository.class);
        mensajes = mock(WhatsappMensajeRepository.class);
        guardados = new HashMap<>();
        a = new Comercio();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.setWaWabaId("222");
        a.setWaPhoneNumberId("111");
        a.setWaActivo(false);
        contacto = new WhatsappContacto();
        contacto.setComercio(a);
        contacto.setTelefono("5491100000000");
        when(comercios.findByWaPhoneNumberId("111")).thenReturn(Optional.of(a));
        when(contactos.findByComercioIdAndTelefono(1L, "5491100000000")).thenReturn(Optional.of(contacto));
        when(mensajes.findByMetaId(any())).thenAnswer(i -> Optional.ofNullable(guardados.get(i.getArgument(0))));
        when(mensajes.saveAndFlush(any())).thenAnswer(i -> {
            WhatsappMensaje m = i.getArgument(0);
            guardados.put(m.getMetaId(), m);
            return m;
        });
        service = new WhatsappGiroService(comercios, mock(ProductoRepository.class), contactos, mensajes,
                new ObjectMapper(), mock(WhatsappCifrado.class), "verify", "app", "secret", "config", "http://127.0.0.1:1", "");
    }

    @Test void ecoEnMessagesNoSeTrataComoClienteNiAbreVentanaDeAtencion() {
        String event = """
            {"entry":[{"id":"222","changes":[{"field":"smb_message_echoes","value":{
            "metadata":{"phone_number_id":"111"},"messages":[{"id":"eco","from":"5491199999999",
            "to":"5491100000000","type":"text","text":{"body":"desde celular"},"timestamp":"1700000000"}]}}]}]}
            """;
        service.recibir(event);
        service.recibir(event);
        assertEquals(1, guardados.size());
        assertEquals("TELEFONO", guardados.get("eco").getOrigen());
        assertEquals("SALIENTE", guardados.get("eco").getDireccion());
        assertNull(contacto.getUltimoClienteEn());
        assertEquals(0, contacto.getNoLeidos());
    }

    @Test void estadoDeOtroComercioNoSeActualiza() {
        Comercio b = new Comercio();
        ReflectionTestUtils.setField(b, "id", 2L);
        WhatsappContacto deB = new WhatsappContacto();
        deB.setComercio(b);
        WhatsappMensaje mensajeB = new WhatsappMensaje();
        mensajeB.setContacto(deB);
        mensajeB.setEstado("ACEPTADO");
        guardados.put("mensaje-de-b", mensajeB);
        service.recibir("""
            {"entry":[{"id":"222","changes":[{"field":"messages","value":{
            "metadata":{"phone_number_id":"111"},"statuses":[{"id":"mensaje-de-b","status":"read"}]}}]}]}
            """);
        assertEquals("ACEPTADO", mensajeB.getEstado());
    }

    @Test void noProcesaMetadataDeUnNumeroConWabaDistinta() {
        service.recibir("""
            {"entry":[{"id":"otra-waba","changes":[{"field":"messages","value":{
            "metadata":{"phone_number_id":"111"},"messages":[{"id":"falso","from":"5491100000000",
            "type":"text","text":{"body":"hola"}}]}}]}]}
            """);
        assertTrue(guardados.isEmpty());
    }

    @Test void mensajeFueraDeOrdenNoRetrocedeUltimoMensajeNiVentana() {
        LocalDateTime reciente = LocalDateTime.now();
        contacto.setUltimoClienteEn(reciente);
        contacto.setUltimoEn(reciente);
        contacto.setUltimoTexto("mensaje reciente");
        service.recibir("""
            {"entry":[{"id":"222","changes":[{"field":"messages","value":{
            "metadata":{"phone_number_id":"111"},"messages":[{"id":"viejo","from":"5491100000000",
            "type":"text","text":{"body":"viejo"},"timestamp":"1700000000"}]}}]}]}
            """);
        assertEquals(reciente, contacto.getUltimoClienteEn());
        assertEquals("mensaje reciente", contacto.getUltimoTexto());
        assertEquals(1, guardados.size());
    }
}
