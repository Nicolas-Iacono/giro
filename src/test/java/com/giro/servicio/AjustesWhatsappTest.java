package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.Comercio;
import com.giro.domain.WhatsappVinculo;
import com.giro.error.NegocioException;
import com.giro.repo.*;
import com.giro.seguridad.Sesion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AjustesWhatsappTest {
    AjustesService service;
    WhatsappGiroService whatsapp;
    Comercio comercio;
    WhatsappVinculo vinculo;
    Map<String, Object> meta;

    @BeforeEach void setup() {
        Sesion sesion = mock(Sesion.class);
        var comercios = mock(ComercioRepository.class);
        var vinculos = mock(WhatsappVinculoRepository.class);
        whatsapp = mock(WhatsappGiroService.class);
        comercio = new Comercio();
        ReflectionTestUtils.setField(comercio, "id", 1L);
        when(sesion.comercioId()).thenReturn(1L);
        when(comercios.findById(1L)).thenReturn(Optional.of(comercio));
        when(whatsapp.appId()).thenReturn("jiro-app");
        when(whatsapp.configId()).thenReturn("config-jiro");
        vinculo = new WhatsappVinculo();
        vinculo.setComercioId(1L);
        vinculo.setClave("state");
        vinculo.setVenceEn(LocalDateTime.now().plusMinutes(5));
        when(vinculos.findByClave("state")).thenReturn(Optional.of(vinculo));
        meta = new HashMap<>(Map.of("phoneNumberId", "111111", "wabaId", "222222", "accessToken", "secret-token",
                "tipo", "COEXISTENCIA", "webhookSuscripto", true, "sincronizacionPedida", false));
        when(whatsapp.conectar(anyString(), any(), any(), any())).thenReturn(meta);
        service = new AjustesService(sesion, comercios, mock(VidrieraRepository.class), whatsapp, vinculos, "https://backend.test");
    }

    private Modelos.ConectarWhatsapp entrada() { return new Modelos.ConectarWhatsapp("code", "111111", "222222", "COEXISTENCIA", "state"); }

    @Test void suscripcionFallidaQuedaPendienteSinActivarBot() {
        meta.put("webhookSuscripto", false);
        meta.put("avisoSuscripcion", "Error de recepción");
        var result = service.conectarWhatsapp(entrada());
        assertEquals("PENDIENTE_WEBHOOK", result.get("waEstado"));
        assertEquals(false, result.get("waConectado"));
        assertFalse(comercio.isWaActivo());
        verify(whatsapp, never()).pedirSincronizacion(any(), any());
        assertFalse(result.containsKey("accessToken"));
    }

    @Test void conexionCorrectaNoActivaBotNiSincronizaAntesDeConfirmarRespuesta() {
        var result = service.conectarWhatsapp(entrada());
        assertEquals("CONECTADO", result.get("waEstado"));
        assertFalse(comercio.isWaActivo());
        assertEquals("NO_PEDIDO", result.get("waHistorial"));
        assertTrue(vinculo.isUsado());
        verify(whatsapp).guardarToken(comercio, "secret-token");
        verify(whatsapp, never()).pedirSincronizacion(any(), any());
    }

    @Test void stateDeOtroComercioNoCanjeaElCodigo() {
        vinculo.setComercioId(2L);
        assertThrows(NegocioException.class, () -> service.conectarWhatsapp(entrada()));
        verify(whatsapp, never()).conectar(any(), any(), any(), any());
    }

    @Test void stateVencidoNoCanjeaElCodigo() {
        vinculo.setVenceEn(LocalDateTime.now().minusSeconds(1));
        assertThrows(NegocioException.class, () -> service.conectarWhatsapp(entrada()));
        verify(whatsapp, never()).conectar(any(), any(), any(), any());
    }

    @Test void stateUsadoNoCanjeaOtraVez() {
        vinculo.setUsado(true);
        assertThrows(NegocioException.class, () -> service.conectarWhatsapp(entrada()));
        verify(whatsapp, never()).conectar(any(), any(), any(), any());
    }
}
