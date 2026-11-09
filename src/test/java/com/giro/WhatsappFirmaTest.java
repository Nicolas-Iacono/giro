package com.giro;

import com.giro.servicio.WhatsappCifrado;
import com.giro.servicio.WhatsappFirma;
import com.giro.error.NegocioException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsappFirmaTest {
    @Test
    void aceptaLaFirmaDelCuerpoOriginalYRechazaOtra() throws Exception {
        byte[] cuerpo = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String firma = firmar(cuerpo, "secreto-de-prueba-giro");
        assertTrue(WhatsappFirma.valida(cuerpo, firma, "secreto-de-prueba-giro"));
        assertFalse(WhatsappFirma.valida(cuerpo, firma, "otro-secreto"));
        assertFalse(WhatsappFirma.valida("{\"entry\":[ ]}".getBytes(StandardCharsets.UTF_8), firma, "secreto-de-prueba-giro"));
        assertFalse(WhatsappFirma.valida(cuerpo, firma, ""));
        assertFalse(WhatsappFirma.valida(cuerpo, null, "secreto-de-prueba-giro"));
    }

    @Test
    void reconoceLaRevocacionDeMeta() {
        assertTrue(WhatsappFirma.esRevocacion(190));
        assertTrue(WhatsappFirma.esRevocacion(463));
        assertFalse(WhatsappFirma.esRevocacion(100));
    }

    @Test
    void elTokenSeLeeSoloConLaMismaClave() {
        WhatsappCifrado cifrado = new WhatsappCifrado("clave-de-prueba-para-tokens");
        String guardado = cifrado.cifrar("EAA-token-del-comercio");
        assertTrue(guardado.startsWith("enc:v1:"));
        assertEquals("EAA-token-del-comercio", cifrado.leer(guardado));
        assertThrows(NegocioException.class, () -> new WhatsappCifrado("otra-clave").leer(guardado));
        assertThrows(NegocioException.class, () -> new WhatsappCifrado("").cifrar("EAA-token-del-comercio"));
    }

    private String firmar(byte[] cuerpo, String secreto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo));
    }
}
