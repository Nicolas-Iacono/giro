package com.giro.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giro.error.NegocioException;
import com.giro.repo.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WhatsappConexionTest {
    HttpServer server;
    WhatsappGiroService service;
    String tokenApp = "jiro-app";
    boolean valid = true;
    String scopes = "\"whatsapp_business_management\",\"whatsapp_business_messaging\"";
    String granular = "[]";
    String onBizApp = "true";
    boolean failSubscription;
    boolean failPhone;
    AtomicInteger subscribed = new AtomicInteger();
    AtomicInteger syncs = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            int status = 200;
            if (path.equals("/oauth/access_token")) body = "{\"access_token\":\"token-test\"}";
            else if (path.equals("/debug_token")) body = "{\"data\":{\"is_valid\":" + valid
                    + ",\"app_id\":\"" + tokenApp + "\",\"scopes\":[" + scopes + "],\"granular_scopes\":" + granular + "}}";
            else if (path.equals("/222/phone_numbers")) body = "{\"data\":[{\"id\":\"111\",\"is_on_biz_app\":true}]}";
            else if (path.equals("/111") && !failPhone) body = "{\"id\":\"111\",\"display_phone_number\":\"5491100000000\",\"verified_name\":\"Prueba\",\"is_on_biz_app\":" + onBizApp + "}";
            else if (path.equals("/222/subscribed_apps")) {
                subscribed.incrementAndGet();
                status = failSubscription ? 400 : 200;
                body = failSubscription ? "{\"error\":{\"code\":100,\"error_subcode\":33,\"message\":\"Unsupported request\"}}" : "{\"success\":true}";
            } else if (path.equals("/jiro-app/subscriptions")) body = "{\"success\":true}";
            else if (path.equals("/111/smb_app_data")) {
                syncs.incrementAndGet();
                body = "{\"success\":true}";
            } else {
                status = 400;
                body = "{\"error\":{\"code\":100,\"error_subcode\":33,\"message\":\"Unsupported request\"}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        service = new WhatsappGiroService(mock(ComercioRepository.class), mock(ProductoRepository.class),
                mock(WhatsappContactoRepository.class), mock(WhatsappMensajeRepository.class), new ObjectMapper(), mock(WhatsappCifrado.class),
                "verify-test", "jiro-app", "secret-test", "config-test",
                "http://127.0.0.1:" + server.getAddress().getPort(), "https://backend.test");
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void conectaSinTargetIdsSiLaApiConfirmaPertenencia() {
        Map<String, Object> result = service.conectar("code", "111", "222", "");
        assertEquals("COEXISTENCIA", result.get("tipo"));
        assertEquals(true, result.get("webhookSuscripto"));
        assertEquals(1, subscribed.get());
        // La sincronización no debe dispararse antes de persistir la vinculación.
        assertEquals(0, syncs.get());
        assertTrue(service.pedirSincronizacion("111", "token-test"));
        assertEquals(2, syncs.get());
    }

    @Test void rechazaTokenDeTuinmoAntesDeSuscribir() {
        tokenApp = "tuinmo-app";
        assertTrue(assertThrows(NegocioException.class, () -> service.conectar("code", "111", "222", "COEXISTENCIA")).getMessage().contains("otra app"));
        assertEquals(0, subscribed.get());
    }

    @Test void rechazaTokenSinPermisosDeMensajeriaYAdministracion() {
        scopes = "\"whatsapp_business_manage_events\"";
        assertTrue(assertThrows(NegocioException.class, () -> service.conectar("code", "111", "222", "COEXISTENCIA")).getMessage().contains("whatsapp_business_management"));
        assertEquals(0, subscribed.get());
    }

    @Test void rechazaTokenRevocado() {
        valid = false;
        assertThrows(NegocioException.class, () -> service.conectar("code", "111", "222", "COEXISTENCIA"));
        assertEquals(0, subscribed.get());
    }

    @Test void noConfirmaCoexistenciaPorUnFlagDelFrontend() {
        onBizApp = "false";
        assertTrue(assertThrows(NegocioException.class, () -> service.conectar("code", "111", "222", "COEXISTENCIA")).getMessage().contains("no confirmó coexistencia"));
        assertEquals(0, subscribed.get());
    }

    @Test void errorDeSuscripcionNoSePresentaComoWebhookListo() {
        failSubscription = true;
        var result = service.conectar("code", "111", "222", "COEXISTENCIA");
        assertEquals(false, result.get("webhookSuscripto"));
        assertTrue(result.get("avisoSuscripcion").toString().contains("falta completar el webhook"));
    }

    @Test void noEligeUnaWabaArbitrariaEntreVarias() {
        granular = "[{\"scope\":\"whatsapp_business_management\",\"target_ids\":[\"222\",\"333\"]}]";
        assertTrue(assertThrows(NegocioException.class, () -> service.conectar("code", "", "", "COEXISTENCIA")).getMessage().contains("varias cuentas"));
        assertEquals(0, subscribed.get());
    }

    @Test void validaIdsManualesContraLaMismaCuenta() {
        assertEquals(true, service.validarCredenciales("111", "222", "token-test").get("ok"));
        assertThrows(NegocioException.class, () -> service.validarCredenciales("999", "222", "token-test"));
    }

    @Test void error10033NoAfirmaQueSeBorroElNumero() {
        failPhone = true;
        String error = assertThrows(NegocioException.class, () -> service.probar("111", "token-test")).getMessage();
        assertTrue(error.contains("100/33"));
        assertTrue(error.contains("no demuestra"));
        assertFalse(error.contains("token-test"));
    }
}
