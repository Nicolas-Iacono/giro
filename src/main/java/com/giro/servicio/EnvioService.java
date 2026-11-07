package com.giro.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giro.api.Modelos;
import com.giro.domain.Comercio;
import com.giro.domain.Pedido;
import com.giro.error.NegocioException;
import com.giro.repo.ComercioRepository;
import com.giro.seguridad.Sesion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class EnvioService {
    private static final double RADIO_KM = 8;
    private final Sesion sesion;
    private final ComercioRepository comercios;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Map<String, double[]> lugares = new HashMap<>();

    public EnvioService(Sesion sesion, ComercioRepository comercios, ObjectMapper json) {
        this.sesion = sesion;
        this.comercios = comercios;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ver() {
        return vista(comercio());
    }

    @Transactional
    public Map<String, Object> guardar(Modelos.TarifaEnvio entrada) {
        Comercio comercio = comercio();
        comercio.setCalle(exigir(entrada.direccion(), 120, "la dirección del local"));
        comercio.setAltura(exigir(entrada.altura(), 16, "la altura del local"));
        comercio.setLocalidad(exigir(entrada.localidad(), 80, "la localidad del local"));
        comercio.setPartido(exigir(entrada.partido(), 80, "el partido del local"));
        comercio.setProvincia(Provincias.canon(entrada.provincia()));
        if (comercio.getProvincia() == null) throw new NegocioException("Elegí la provincia del local");
        comercio.setCodigoPostal(Provincias.codigoPostal(entrada.codigoPostal()));
        if (comercio.getCodigoPostal() == null) throw new NegocioException("Completá el código postal del local");
        comercio.setEnvioBase(Textos.dinero(entrada.base(), "La base del envío"));
        comercio.setEnvioKm(Textos.dinero(entrada.porKm(), "El monto por kilómetro"));
        double[] origen = geocodificar(consulta(comercio.getCalle(), comercio.getAltura(), comercio.getLocalidad(), comercio.getPartido(), comercio.getProvincia(), comercio.getCodigoPostal()));
        if (origen == null) throw new NegocioException("No encontré la dirección del local en el mapa");
        return vista(comercio);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> cotizar(Modelos.CotizarEnvio entrada) {
        Comercio comercio = comercio();
        exigirTarifa(comercio);
        double[] origen = origen(comercio);
        double[] destino = destino(entrada.direccion(), entrada.altura(), entrada.localidad(), entrada.partido(), entrada.provincia(), entrada.codigoPostal());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domicilio", mapa(tramo(origen, destino[0], destino[1], null, comercio)));
        Nodo punto = cercano(destino[0], destino[1]);
        body.put("punto", punto == null ? null : mapa(tramo(origen, punto.lat, punto.lon, punto.nombre, comercio)));
        if (punto == null) body.put("aviso", "No pude ubicar un punto de retiro. El envío a domicilio queda calculado.");
        return body;
    }

    public void aplicar(Pedido pedido) {
        if ("LOCAL".equals(pedido.getEntrega())) {
            pedido.setEnvio(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            pedido.setDistanciaKm(null);
            pedido.setPuntoNombre(null);
            return;
        }
        Comercio comercio = comercio();
        exigirTarifa(comercio);
        double[] origen = origen(comercio);
        double[] destino = destino(pedido.getCalle(), pedido.getAltura(), pedido.getLocalidad(), pedido.getPartido(), pedido.getProvincia(), pedido.getCodigoPostal());
        Tramo tramo;
        if ("PUNTO".equals(pedido.getEntrega())) {
            Nodo punto = cercano(destino[0], destino[1]);
            if (punto == null) throw new NegocioException("No hay un correo o locker a menos de 8 km de esa dirección");
            tramo = tramo(origen, punto.lat, punto.lon, punto.nombre, comercio);
        } else {
            tramo = tramo(origen, destino[0], destino[1], null, comercio);
        }
        pedido.setEnvio(tramo.costo);
        pedido.setDistanciaKm(tramo.km);
        pedido.setPuntoNombre(tramo.nombre);
    }

    private Comercio comercio() {
        return comercios.findById(sesion.comercioId()).orElseThrow();
    }

    private void exigirTarifa(Comercio comercio) {
        if (comercio.getEnvioBase() == null || comercio.getEnvioKm() == null || comercio.getCalle() == null || comercio.getLocalidad() == null) {
            throw new NegocioException("Definí la dirección del local y la tarifa de envío: un monto base y un monto por kilómetro");
        }
    }

    private double[] origen(Comercio comercio) {
        double[] punto = geocodificar(consulta(comercio.getCalle(), comercio.getAltura(), comercio.getLocalidad(), comercio.getPartido(), comercio.getProvincia(), comercio.getCodigoPostal()));
        if (punto == null) throw new NegocioException("No encontré la dirección del local en el mapa");
        return punto;
    }

    private double[] destino(String calle, String altura, String localidad, String partido, String provincia, String cp) {
        double[] punto = geocodificar(consulta(calle, altura, localidad, partido, provincia, cp));
        if (punto == null) throw new NegocioException("No encontré ese domicilio en el mapa");
        return punto;
    }

    private Tramo tramo(double[] origen, double lat, double lon, String nombre, Comercio comercio) {
        BigDecimal km = BigDecimal.valueOf(distancia(origen[0], origen[1], lat, lon)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal costo = comercio.getEnvioBase().add(km.multiply(comercio.getEnvioKm())).setScale(2, RoundingMode.HALF_UP);
        return new Tramo(km, costo, lat, lon, nombre);
    }

    private Map<String, Object> mapa(Tramo tramo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("km", tramo.km);
        body.put("costo", tramo.costo);
        body.put("lat", tramo.lat);
        body.put("lon", tramo.lon);
        body.put("nombre", tramo.nombre);
        return body;
    }

    private Map<String, Object> vista(Comercio comercio) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("direccion", comercio.getCalle());
        body.put("altura", comercio.getAltura());
        body.put("localidad", comercio.getLocalidad());
        body.put("partido", comercio.getPartido());
        body.put("provincia", comercio.getProvincia());
        body.put("codigoPostal", comercio.getCodigoPostal());
        body.put("base", comercio.getEnvioBase());
        body.put("porKm", comercio.getEnvioKm());
        return body;
    }

    private Nodo cercano(double lat, double lon) {
        String consulta = String.format(Locale.US,
                "[out:json][timeout:12];(node[\"amenity\"=\"post_office\"](around:8000,%f,%f);node[\"amenity\"=\"parcel_locker\"](around:8000,%f,%f););out body;",
                lat, lon, lat, lon);
        JsonNode elementos;
        try {
            String cuerpo = "data=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8);
            elementos = json.readTree(pedir("https://overpass-api.de/api/interpreter", cuerpo, Duration.ofSeconds(4))).path("elements");
        } catch (RuntimeException error) {
            return null;
        } catch (Exception error) {
            return null;
        }
        Nodo mejor = null;
        double menor = Double.MAX_VALUE;
        for (JsonNode nodo : elementos) {
            if (!nodo.hasNonNull("lat") || !nodo.hasNonNull("lon")) continue;
            double nlat = nodo.get("lat").asDouble();
            double nlon = nodo.get("lon").asDouble();
            double km = distancia(lat, lon, nlat, nlon);
            if (km > RADIO_KM || km >= menor) continue;
            menor = km;
            String nombre = nodo.path("tags").path("name").asText("").trim();
            if (nombre.isBlank()) nombre = "Punto de retiro";
            mejor = new Nodo(nombre, nlat, nlon);
        }
        return mejor;
    }

    private synchronized double[] geocodificar(String consulta) {
        double[] guardado = lugares.get(consulta);
        if (guardado != null) return guardado;
        String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ar&q="
                + URLEncoder.encode(consulta, StandardCharsets.UTF_8);
        double[] punto = null;
        try {
            JsonNode lista = json.readTree(pedir(url, null));
            if (lista.isArray() && !lista.isEmpty()) {
                punto = new double[] { lista.get(0).get("lat").asDouble(), lista.get(0).get("lon").asDouble() };
            }
        } catch (NegocioException error) {
            throw error;
        } catch (Exception error) {
            throw new NegocioException("No se pudo ubicar la dirección en el mapa");
        }
        if (punto != null) lugares.put(consulta, punto);
        return punto;
    }

    private String pedir(String url, String cuerpo) {
        return pedir(url, cuerpo, Duration.ofSeconds(12));
    }

    private String pedir(String url, String cuerpo, Duration espera) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(espera)
                    .header("User-Agent", "GIRO/1.0 (calculo de envio)")
                    .header("Accept", "application/json");
            HttpRequest request = cuerpo == null
                    ? builder.GET().build()
                    : builder.header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
            HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 400) throw new NegocioException("No se pudo consultar el mapa");
            return respuesta.body();
        } catch (NegocioException error) {
            throw error;
        } catch (Exception error) {
            throw new NegocioException("No se pudo consultar el mapa");
        }
    }

    private static double distancia(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String consulta(String calle, String altura, String localidad, String partido, String provincia, String cp) {
        return String.join(", ", java.util.Arrays.stream(new String[] { calle, altura, localidad, partido, provincia, cp, "Argentina" })
                .filter(valor -> valor != null && !valor.isBlank())
                .map(String::trim)
                .toList());
    }

    private static String exigir(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) throw new NegocioException("Completá " + campo);
        String limpio = valor.trim();
        if (limpio.length() > maximo) throw new NegocioException("Revisá " + campo + ": tiene demasiados caracteres");
        return limpio;
    }

    private record Tramo(BigDecimal km, BigDecimal costo, double lat, double lon, String nombre) {}

    private record Nodo(String nombre, double lat, double lon) {}
}
