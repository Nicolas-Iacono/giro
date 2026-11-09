package com.giro;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AislamientoComercioTest {
    @Autowired
    MockMvc mvc;

    @Test
    void unComercioNoOperaDatosDeOtro() throws Exception {
        String tokenA = registrar("Kiosco A", "kiosco-a", "a@giro.test");
        String tokenB = registrar("Kiosco B", "kiosco-b", "b@giro.test");

        MvcResult creado = mvc.perform(post("/api/productos")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Alfajor","costo":100,"precio":250,"stock":10,"stockMinimo":2,"publicado":true,"usaVariantes":false}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Number id = JsonPath.read(creado.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Robado","costo":1,"precio":1,"stock":1,"stockMinimo":0,"publicado":false,"usaVariantes":false}
                                """))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/caja")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ventas")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medio":"EFECTIVO","items":[{"productoId":%s,"cantidad":1}]}
                                """.formatted(id)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/public/vidrieras"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/public/vidrieras/kiosco-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos", hasSize(1)))
                .andExpect(jsonPath("$.productos[0].nombre").value("Alfajor"));

        mvc.perform(get("/api/caja")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ventas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"medio":"EFECTIVO","items":[{"productoId":%s,"cantidad":2}]}
                                """.formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(500.0));

        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$[0].stock").value(8.0));

        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.ventasDelDia").value(500.0))
                .andExpect(jsonPath("$.gananciaBrutaDelMes").value(300.0));

        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.ventasDelDia").value(0));
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
