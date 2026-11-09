package com.giro;

import com.giro.domain.Comercio;
import com.giro.repo.ComercioRepository;
import com.giro.repo.WhatsappMensajeRepository;
import com.giro.servicio.WhatsappCola;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsappWebhookTest {
    @Autowired MockMvc mvc;
    @Autowired ComercioRepository comercios;
    @Autowired WhatsappMensajeRepository mensajes;
    @Autowired WhatsappCola cola;
    @Autowired com.giro.servicio.WhatsappGiroService whatsapp;

    @Test
    void firmaAislamientoEcoDuplicadoYRevocacion() throws Exception {
        String tokenA = registrar("Local A", "local-a", "wa-a@giro.test");
        String tokenB = registrar("Local B", "local-b", "wa-b@giro.test");
        Comercio a = comercios.findBySlug("local-a").orElseThrow();
        Comercio b = comercios.findBySlug("local-b").orElseThrow();
        a.setWaPhoneNumberId("111111111111111");
        a.setWaWabaId("222222222222222");
        a.setWaNumero("5491111111111");
        a.setWaEstado("CONECTADO");
        a.setWaActivo(false);
        whatsapp.guardarToken(a, "EAA-token-de-prueba-largo");
        comercios.save(a);
        b.setWaPhoneNumberId("333333333333333");
        b.setWaEstado("CONECTADO");
        comercios.save(b);

        String entrante = """
                {"entry":[{"id":"222222222222222","changes":[{"field":"messages","value":{"metadata":{"phone_number_id":"111111111111111","display_phone_number":"5491111111111"},"messages":[{"from":"5491100000000","id":"wamid.entrada","timestamp":"1700000000","type":"text","text":{"body":"hola"}}]}}]}]}
                """;
        mvc.perform(post("/api/public/whatsapp").contentType(MediaType.APPLICATION_JSON).content(entrante))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/public/whatsapp").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firmar(entrante.getBytes(StandardCharsets.UTF_8)))
                        .content(entrante))
                .andExpect(status().isOk());
        mvc.perform(post("/api/public/whatsapp").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firmar(entrante.getBytes(StandardCharsets.UTF_8)))
                        .content(entrante))
                .andExpect(status().isOk());
        cola.drenar();
        assertEquals(1, mensajes.count());

        String eco = """
                {"entry":[{"id":"222222222222222","changes":[{"field":"smb_message_echoes","value":{"metadata":{"phone_number_id":"111111111111111"},"message_echoes":[{"from":"5491111111111","to":"5491100000000","id":"wamid.eco","timestamp":"1700000005","type":"text","text":{"body":"desde el celular"}}]}}]}]}
                """;
        mvc.perform(post("/api/public/whatsapp").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firmar(eco.getBytes(StandardCharsets.UTF_8)))
                        .content(eco))
                .andExpect(status().isOk());
        cola.drenar();
        assertEquals(2, mensajes.count());
        assertEquals(1, mensajes.findAll().stream().filter(m -> "TELEFONO".equals(m.getOrigen())).count());

        String mismoId = """
                {"entry":[{"id":"222222222222222","changes":[{"field":"smb_message_echoes","value":{"metadata":{"phone_number_id":"111111111111111"},"message_echoes":[{"from":"5491111111111","to":"5491100000000","id":"wamid.eco","timestamp":"1700000009","type":"text","text":{"body":"otro texto"}}]}}]}]}
                """;
        mvc.perform(post("/api/public/whatsapp").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firmar(mismoId.getBytes(StandardCharsets.UTF_8)))
                        .content(mismoId))
                .andExpect(status().isOk());
        cola.drenar();
        assertEquals(2, mensajes.count());

        mvc.perform(get("/api/whatsapp/conversaciones").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
        mvc.perform(get("/api/whatsapp/conversaciones").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(1));

        whatsapp.marcarRevocado(comercios.findBySlug("local-a").orElseThrow());
        Comercio revocado = comercios.findBySlug("local-a").orElseThrow();
        assertEquals("RECONEXION", revocado.getWaEstado());
        assertNull(revocado.getWaAccessToken());
        assertEquals("111111111111111", revocado.getWaPhoneNumberId());

        mvc.perform(get("/api/public/whatsapp/"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/ajustes/whatsapp/conectar")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc\",\"state\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String firmar(byte[] cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secreto-de-prueba-giro".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo));
    }

    private String registrar(String nombre, String slug, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreComercio":"%s","slug":"%s","email":"%s","password":"secreto12","nombre":"Ana"}
                                """.formatted(nombre, slug, email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}
