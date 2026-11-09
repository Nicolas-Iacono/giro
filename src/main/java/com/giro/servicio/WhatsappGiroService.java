package com.giro.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giro.domain.Comercio;
import com.giro.domain.Producto;
import com.giro.domain.WhatsappContacto;
import com.giro.domain.WhatsappMensaje;
import com.giro.error.NegocioException;
import com.giro.error.TokenRevocadoException;
import com.giro.repo.ComercioRepository;
import com.giro.repo.ProductoRepository;
import com.giro.repo.WhatsappContactoRepository;
import com.giro.repo.WhatsappMensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WhatsappGiroService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappGiroService.class);
    static final String CAMPOS_WEBHOOK = "messages,history,smb_app_state_sync,smb_message_echoes,account_update";

    private final ComercioRepository comercios;
    private final ProductoRepository productos;
    private final WhatsappContactoRepository contactos;
    private final WhatsappMensajeRepository mensajes;
    private final ObjectMapper json;
    private final WhatsappCifrado cifrado;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String verifyToken;
    private final String appId;
    private final String appSecret;
    private final String configId;
    private final String graph;
    private final String urlPublica;

    public WhatsappGiroService(ComercioRepository comercios, ProductoRepository productos,
                               WhatsappContactoRepository contactos, WhatsappMensajeRepository mensajes, ObjectMapper json,
                               WhatsappCifrado cifrado,
                               @Value("${giro.whatsapp.verify-token:}") String verifyToken,
                               @Value("${giro.whatsapp.app-id:}") String appId,
                               @Value("${giro.whatsapp.app-secret:}") String appSecret,
                               @Value("${giro.whatsapp.config-id:}") String configId,
                               @Value("${giro.whatsapp.graph:https://graph.facebook.com/v21.0}") String graph,
                               @Value("${giro.public-url:}") String urlPublica) {
        this.comercios = comercios;
        this.productos = productos;
        this.contactos = contactos;
        this.mensajes = mensajes;
        this.json = json;
        this.cifrado = cifrado;
        this.verifyToken = verifyToken == null ? "" : verifyToken.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.configId = configId == null ? "" : configId.trim();
        this.graph = graph.endsWith("/") ? graph.substring(0, graph.length() - 1) : graph;
        String publica = urlPublica == null ? "" : urlPublica.trim();
        this.urlPublica = publica.endsWith("/") ? publica.substring(0, publica.length() - 1) : publica;
    }

    public String appId() { return appId; }

    public String configId() { return configId; }

    public ResponseEntity<String> verificar(String modo, String token, String challenge) {
        if (!"subscribe".equals(modo) || challenge == null || challenge.isBlank() || !WhatsappFirma.token(verifyToken, token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("verify token");
        }
        return ResponseEntity.ok(challenge);
    }

    public boolean firmaValida(byte[] cuerpo, String firma) {
        return WhatsappFirma.valida(cuerpo, firma, appSecret);
    }

    public String tokenPlano(Comercio comercio) {
        return cifrado.leer(comercio.getWaAccessToken());
    }

    public void guardarToken(Comercio comercio, String plano) {
        comercio.setWaAccessToken(cifrado.cifrar(plano));
    }

    public String enmascarar(Comercio comercio) {
        String plano;
        try {
            plano = tokenPlano(comercio);
        } catch (NegocioException ex) {
            return null;
        }
        if (plano == null || plano.isBlank()) return null;
        return "***" + plano.substring(Math.max(0, plano.length() - 4));
    }

    @Transactional
    public void recibir(String raw) {
        JsonNode raiz;
        try {
            raiz = raw == null || raw.isBlank() ? json.createObjectNode() : json.readTree(raw);
        } catch (Exception ex) {
            throw new NegocioException("El webhook de WhatsApp no es JSON");
        }
        for (JsonNode entry : raiz.path("entry")) {
            String wabaEntrada = texto(entry.path("id"));
            for (JsonNode change : entry.path("changes")) {
                String campo = texto(change.path("field"));
                JsonNode value = change.path("value");
                if ("account_update".equals(campo)) {
                    cuenta(value, wabaEntrada);
                    continue;
                }
                String phoneId = texto(value.path("metadata").path("phone_number_id"));
                Comercio comercio = phoneId == null ? null : comercios.findByWaPhoneNumberId(phoneId).orElse(null);
                if (comercio == null) continue;
                String display = texto(value.path("metadata").path("display_phone_number"));
                if (display != null && !display.equals(comercio.getWaNumero())) comercio.setWaNumero(display);
                if ("history".equals(campo) || value.has("history")) historial(comercio, value);
                if ("smb_app_state_sync".equals(campo) || value.has("state_sync")) contactosDeApp(comercio, value);
                if (value.has("messages") || "messages".equals(campo)) {
                    for (JsonNode mensaje : value.path("messages")) entrante(comercio, phoneId, value, mensaje);
                    for (JsonNode status : value.path("statuses")) estadoMeta(status);
                }
                JsonNode ecos = value.has("message_echoes") ? value.path("message_echoes") : value.path("smb_message_echoes");
                for (JsonNode eco : ecos) ecoDeCelular(comercio, eco);
            }
        }
    }

    @Transactional
    public void marcarRevocado(Comercio comercio) {
        comercio = comercios.findById(comercio.getId()).orElse(comercio);
        comercio.setWaEstado("RECONEXION");
        comercio.setWaActivo(false);
        comercio.setWaRevocadoEn(LocalDateTime.now());
        comercio.setWaAccessToken(null);
        comercio.setWaAviso("Meta revocó el acceso. El número sigue en el celular. Volvé a conectar WhatsApp Business.");
    }

    public void desconectar(Comercio comercio) {
        comercio = comercios.findById(comercio.getId()).orElse(comercio);
        String token = null;
        try {
            token = tokenPlano(comercio);
        } catch (NegocioException ex) {
            token = null;
        }
        String waba = comercio.getWaWabaId();
        boolean corto = false;
        if (token != null && waba != null && !waba.isBlank()) {
            try {
                pedir("DELETE", "/" + waba + "/subscribed_apps", token, null);
                corto = true;
            } catch (RuntimeException ex) {
                log.warn("No se pudo desuscribir WhatsApp: {}", ex.getClass().getSimpleName());
            }
        }
        comercio.setWaAccessToken(null);
        comercio.setWaPhoneNumberId(null);
        comercio.setWaWabaId(null);
        comercio.setWaActivo(false);
        comercio.setWaEstado("DESCONECTADO");
        comercio.setWaAviso(corto
                ? "Jiro ya no envía por este número. El WhatsApp del celular sigue activo."
                : "Jiro ya no envía por este número. El acceso de Meta se corta en el celular: Ajustes, Cuenta, Plataforma comercial.");
    }

    public Map<String, Object> probar(String phoneId, String token) {
        if (phoneId == null || phoneId.isBlank() || token == null || token.isBlank()) {
            throw new NegocioException("Cargá el Phone Number ID y el Access Token de la app de Giro");
        }
        JsonNode data = graph("GET", "/" + phoneId + "?fields=display_phone_number,verified_name", token, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mensaje", "Giro se conectó con este número de WhatsApp");
        body.put("displayPhoneNumber", texto(data.path("display_phone_number")));
        body.put("verifiedName", texto(data.path("verified_name")));
        return body;
    }

    public Map<String, Object> conectar(String code, String phoneId, String wabaId, String tipoPedido) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new NegocioException("Falta GIRO_WHATSAPP_APP_ID o GIRO_WHATSAPP_APP_SECRET en el servidor de Giro");
        }
        if (code == null || code.isBlank()) throw new NegocioException("Meta no devolvió el código de conexión");
        JsonNode canje = intercambiar(code.trim());
        String token = texto(canje.path("access_token"));
        if (token == null) throw new NegocioException("Meta no entregó el token del comercio");
        java.util.Set<String> cuentas = cuentasDelToken(token);
        String cuenta = wabaId == null || wabaId.isBlank() ? null : wabaId.trim();
        if (cuenta != null && !cuentas.contains(cuenta)) {
            throw new NegocioException("Esa cuenta de WhatsApp no está autorizada por el token de Meta");
        }
        if (cuenta == null) {
            if (cuentas.isEmpty()) throw new NegocioException("Meta no informó la cuenta de WhatsApp de este comercio");
            cuenta = cuentas.iterator().next();
        }
        JsonNode lista = graph("GET", "/" + cuenta + "/phone_numbers?fields=id,display_phone_number,verified_name,is_on_biz_app,platform_type", token, null);
        JsonNode elegido = null;
        for (JsonNode item : lista.path("data")) {
            if (phoneId != null && !phoneId.isBlank() && phoneId.trim().equals(texto(item.path("id")))) elegido = item;
        }
        if (phoneId != null && !phoneId.isBlank() && elegido == null) {
            throw new NegocioException("Ese número no está autorizado para el token de Meta");
        }
        if (elegido == null) {
            for (JsonNode item : lista.path("data")) {
                if (item.path("is_on_biz_app").asBoolean(false)) {
                    elegido = item;
                    break;
                }
            }
        }
        if (elegido == null && lista.path("data").size() == 1) elegido = lista.path("data").get(0);
        if (elegido == null) throw new NegocioException("Meta conectó la cuenta, pero no devolvió el número de WhatsApp Business");
        String numero = texto(elegido.path("id"));
        JsonNode ficha = graph("GET", "/" + numero + "?fields=display_phone_number,verified_name,is_on_biz_app,platform_type", token, null);
        boolean informado = ficha.has("is_on_biz_app") && !ficha.path("is_on_biz_app").isNull();
        boolean coexistencia = informado ? ficha.path("is_on_biz_app").asBoolean(false) : "COEXISTENCIA".equals(tipoPedido);
        boolean suscripto = true;
        String avisoSuscripcion = null;
        try {
            graph("POST", "/" + cuenta + "/subscribed_apps", token, "{}");
        } catch (NegocioException ex) {
            suscripto = false;
            avisoSuscripcion = ex.getMessage();
        }
        boolean sincronizado = false;
        try {
            suscribirCamposApp();
        } catch (NegocioException ex) {
            if (avisoSuscripcion == null) avisoSuscripcion = ex.getMessage();
        }
        if (coexistencia) sincronizado = pedirSincronizacion(numero, token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", token);
        body.put("expiresIn", canje.path("expires_in").isNumber() ? canje.path("expires_in").asLong() : null);
        body.put("phoneNumberId", numero);
        body.put("wabaId", cuenta);
        body.put("displayPhoneNumber", texto(ficha.path("display_phone_number")));
        body.put("verifiedName", texto(ficha.path("verified_name")));
        body.put("tipo", coexistencia ? "COEXISTENCIA" : "ESTANDAR");
        body.put("webhookSuscripto", suscripto);
        body.put("sincronizacionPedida", sincronizado);
        body.put("avisoSuscripcion", avisoSuscripcion);
        return body;
    }

    private boolean pedirSincronizacion(String numero, String token) {
        boolean ok = true;
        for (String tipo : java.util.List.of("smb_app_state_sync", "history")) {
            try {
                graph("POST", "/" + numero + "/smb_app_data", token,
                        "{\"messaging_product\":\"whatsapp\",\"sync_type\":\"" + tipo + "\"}");
            } catch (NegocioException ex) {
                ok = false;
                log.warn("Meta no inició la sincronización {}: {}", tipo, ex.getClass().getSimpleName());
            }
        }
        return ok;
    }

    public Map<String, Object> suscribir(String wabaId, String token) {
        boolean campos = false;
        String avisoCampos = null;
        try {
            suscribirCamposApp();
            campos = true;
        } catch (NegocioException ex) {
            avisoCampos = ex.getMessage();
        }
        boolean pidioWaba = wabaId != null && !wabaId.isBlank() && token != null && !token.isBlank();
        boolean waba = false;
        String avisoWaba = null;
        if (pidioWaba) {
            try {
                graph("POST", "/" + wabaId.trim() + "/subscribed_apps", token, "{}");
                waba = true;
            } catch (NegocioException ex) {
                avisoWaba = ex.getMessage();
            }
        }
        if (!campos && !waba) {
            if (!pidioWaba && avisoCampos != null) throw new NegocioException(avisoCampos);
            if (avisoWaba != null && avisoCampos != null) {
                throw new NegocioException(avisoCampos + " " + avisoWaba);
            }
            throw new NegocioException(avisoWaba != null ? avisoWaba : "Guardá el WABA ID y el Access Token de la app de Giro antes de suscribir");
        }
        String mensaje = campos
                ? "La app de Giro quedó suscripta a messages, history, smb_app_state_sync, smb_message_echoes y account_update."
                : "Este número quedó enganchado a la app de Giro.";
        if (campos && waba) mensaje = mensaje + " Este número también quedó enganchado.";
        String aviso = campos ? avisoWaba : avisoCampos;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("campos", campos);
        body.put("waba", waba);
        body.put("mensaje", mensaje);
        if (aviso != null) body.put("aviso", aviso);
        return body;
    }

    private void suscribirCamposApp() {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new NegocioException("Falta GIRO_WHATSAPP_APP_ID o GIRO_WHATSAPP_APP_SECRET en el servidor de Giro");
        }
        if (verifyToken.isBlank()) {
            throw new NegocioException("Falta GIRO_WHATSAPP_VERIFY_TOKEN en el servidor de Giro");
        }
        if (urlPublica.isBlank()) {
            throw new NegocioException("Falta GIRO_PUBLIC_URL en el servidor de Giro");
        }
        String callback = urlPublica + "/api/public/whatsapp";
        String cuerpo = "object=whatsapp_business_account"
                + "&callback_url=" + java.net.URLEncoder.encode(callback, StandardCharsets.UTF_8)
                + "&verify_token=" + java.net.URLEncoder.encode(verifyToken, StandardCharsets.UTF_8)
                + "&fields=" + java.net.URLEncoder.encode(CAMPOS_WEBHOOK, StandardCharsets.UTF_8);
        pedirForma("/" + appId + "/subscriptions", appId + "|" + appSecret, cuerpo);
    }

    public String textoTransferencia(Comercio comercio) {
        if (vacio(comercio.getMpAlias()) && vacio(comercio.getMpCvu()) && vacio(comercio.getMpCbu())) {
            return null;
        }
        StringBuilder texto = new StringBuilder();
        texto.append("Datos para transferir");
        if (!vacio(comercio.getNombre())) texto.append(" a ").append(comercio.getNombre().trim());
        texto.append(":\n");
        if (!vacio(comercio.getMpTitular())) texto.append("Titular: ").append(comercio.getMpTitular().trim()).append('\n');
        if (!vacio(comercio.getMpAlias())) texto.append("Alias: ").append(comercio.getMpAlias().trim()).append('\n');
        if (!vacio(comercio.getMpCvu())) texto.append("CVU: ").append(comercio.getMpCvu().trim()).append('\n');
        if (!vacio(comercio.getMpCbu())) texto.append("CBU: ").append(comercio.getMpCbu().trim()).append('\n');
        texto.append("Mercado Pago");
        return texto.toString().trim();
    }

    private String responder(Comercio comercio, String escrito) {
        String normal = normalizar(escrito);
        if (contiene(normal, "pago", "transfer", "alias", "cvu", "cbu", "mercado")) return pago(comercio);
        if (contiene(normal, "direccion", "donde", "ubicacion", "mapa", "local")) return direccion(comercio);
        if (contiene(normal, "catalogo", "producto", "precio", "menu", "tienen", "venden")) return catalogo(comercio);
        return saludo(comercio) + "\n\nPuedo pasarte:\n• productos\n• dirección\n• transferencia";
    }

    private String saludo(Comercio comercio) {
        if (!vacio(comercio.getWaSaludo())) return comercio.getWaSaludo().trim();
        return "Hola, soy el asistente de " + comercio.getNombre() + ".";
    }

    private String pago(Comercio comercio) {
        String datos = textoTransferencia(comercio);
        if (datos == null) return "Todavía no cargué los datos de Mercado Pago. Escribile directo al local para coordinar el pago.";
        return datos;
    }

    private String direccion(Comercio comercio) {
        String canal = comercio.getCanal() == null ? "FISICA" : comercio.getCanal();
        StringBuilder texto = new StringBuilder();
        if ("ONLINE".equals(canal)) texto.append("Vendemos online.");
        else if ("AMBAS".equals(canal)) texto.append("Tenemos local y también vendemos online.");
        else texto.append("Estamos en el local.");
        String linea = String.join(" ", java.util.Arrays.stream(new String[] {
                comercio.getCalle(), comercio.getAltura()
        }).filter(valor -> !vacio(valor)).map(String::trim).toList());
        String zona = String.join(", ", java.util.Arrays.stream(new String[] {
                comercio.getLocalidad(), comercio.getPartido(), comercio.getProvincia(), comercio.getCodigoPostal()
        }).filter(valor -> !vacio(valor)).map(String::trim).toList());
        if (!linea.isBlank() || !zona.isBlank()) {
            texto.append("\n").append(linea);
            if (!linea.isBlank() && !zona.isBlank()) texto.append(", ");
            texto.append(zona);
        }
        return texto.toString().trim();
    }

    private String catalogo(Comercio comercio) {
        if ("SERVICIOS".equals(comercio.getOferta())) {
            return saludo(comercio) + "\nTrabajamos con servicios. Contame qué necesitás y lo vemos.";
        }
        List<Producto> lista = productos.findByComercioIdAndActivoTrueAndPublicadoTrueOrderByNombreAsc(comercio.getId());
        if (lista.isEmpty()) return "El catálogo todavía no tiene productos publicados.";
        StringBuilder texto = new StringBuilder("Esto es lo que tenemos publicado:\n");
        int n = 0;
        for (Producto producto : lista) {
            if (n == 8) {
                texto.append("…y hay más en la vidriera.");
                break;
            }
            texto.append("• ").append(producto.getNombre());
            if (producto.getPrecio() != null) texto.append(" — $").append(producto.getPrecio().toPlainString());
            texto.append('\n');
            n++;
        }
        return texto.toString().trim();
    }

    public void enviarTexto(Comercio comercio, String destino, String cuerpo) {
        if (!puedeEnviar(comercio)) throw new NegocioException("Conectá WhatsApp en Ajustes antes de escribir");
        WhatsappContacto contacto = contactos.findByComercioIdAndTelefono(comercio.getId(), digitos(destino)).orElse(null);
        if (contacto == null || contacto.getUltimoClienteEn() == null || contacto.getUltimoClienteEn().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new NegocioException("Pasaron más de 24 horas desde el último mensaje de esta persona. WhatsApp exige una plantilla aprobada y este comercio no tiene una cargada.");
        }
        try {
            String metaId = enviar(comercio.getWaPhoneNumberId(), tokenPlano(comercio), destino, cuerpo);
            anotar(contacto, "SALIENTE", "LOCAL", cuerpo, false, metaId, "ACEPTADO", LocalDateTime.now());
        } catch (TokenRevocadoException ex) {
            marcarRevocado(comercio);
            throw ex;
        }
    }

    private WhatsappContacto contactoDe(Comercio comercio, String telefono, String nombre) {
        WhatsappContacto contacto = contactos.findByComercioIdAndTelefono(comercio.getId(), telefono).orElseGet(() -> {
            WhatsappContacto nuevo = new WhatsappContacto();
            nuevo.setComercio(comercio);
            nuevo.setTelefono(telefono);
            return contactos.save(nuevo);
        });
        if ((contacto.getNombre() == null || contacto.getNombre().isBlank()) && nombre != null) contacto.setNombre(nombre);
        return contacto;
    }

    private void entrante(Comercio comercio, String phoneId, JsonNode value, JsonNode mensaje) {
        String desde = digitos(texto(mensaje.path("from")));
        if (desde == null) return;
        String tipo = texto(mensaje.path("type"));
        String escrito = cuerpoDe(mensaje);
        if (escrito == null) return;
        String metaId = texto(mensaje.path("id"));
        LocalDateTime cuando = epoch(mensaje.path("timestamp"));
        WhatsappContacto contacto = contactoDe(comercio, desde, nombreDe(value, desde));
        if (!anotar(contacto, "ENTRANTE", "CLIENTE", escrito, true, metaId, null, cuando)) return;
        contacto.setUltimoClienteEn(cuando);
        if (!puedeResponder(comercio) || !contacto.isBotActivo() || !"text".equals(tipo)) return;
        try {
            String respuesta = responder(comercio, escrito);
            String idRespuesta = enviar(phoneId, tokenPlano(comercio), desde, respuesta);
            anotar(contacto, "SALIENTE", "BOT", respuesta, false, idRespuesta, "ACEPTADO", LocalDateTime.now());
        } catch (TokenRevocadoException ex) {
            marcarRevocado(comercio);
        } catch (RuntimeException ex) {
            log.warn("Meta no aceptó la respuesta de Giro: {}", ex.getClass().getSimpleName());
        }
    }

    private void ecoDeCelular(Comercio comercio, JsonNode eco) {
        String destino = digitos(texto(eco.path("to")));
        if (destino == null) destino = digitos(texto(eco.path("from")));
        String escrito = cuerpoDe(eco);
        if (destino == null || escrito == null) return;
        WhatsappContacto contacto = contactoDe(comercio, destino, null);
        anotar(contacto, "SALIENTE", "TELEFONO", escrito, false, texto(eco.path("id")), "ENVIADO", epoch(eco.path("timestamp")));
    }

    private void estadoMeta(JsonNode status) {
        String metaId = texto(status.path("id"));
        String nuevo = switch (texto(status.path("status")) == null ? "" : texto(status.path("status"))) {
            case "sent" -> "ENVIADO";
            case "delivered" -> "ENTREGADO";
            case "read" -> "LEIDO";
            case "failed" -> "FALLIDO";
            default -> null;
        };
        if (metaId == null || nuevo == null) return;
        mensajes.findByMetaId(metaId).ifPresent(mensaje -> {
            if (rango(nuevo) >= rango(mensaje.getEstado())) mensaje.setEstado(nuevo);
        });
    }

    private void historial(Comercio comercio, JsonNode value) {
        for (JsonNode error : value.path("errors")) {
            if (error.path("code").asInt(0) == 2593109) {
                comercio.setWaHistorial("RECHAZADO");
                comercio.setWaAviso("La persona no compartió el historial de WhatsApp. Los mensajes nuevos sí van a llegar.");
            }
        }
        boolean hubo = false;
        for (JsonNode bloque : value.path("history")) {
            for (JsonNode hilo : bloque.path("threads")) {
                String telefono = digitos(texto(hilo.path("id")));
                for (JsonNode mensaje : hilo.path("messages")) {
                    String escrito = cuerpoDe(mensaje);
                    String desde = digitos(texto(mensaje.path("from")));
                    if (escrito == null || telefono == null) continue;
                    boolean saliente = desde != null && desde.equals(digitos(comercio.getWaNumero()));
                    WhatsappContacto contacto = contactoDe(comercio, telefono, null);
                    anotar(contacto, saliente ? "SALIENTE" : "ENTRANTE", "HISTORIAL", escrito, false, texto(mensaje.path("id")), null, epoch(mensaje.path("timestamp")));
                    hubo = true;
                }
            }
        }
        if (hubo) {
            comercio.setWaHistorial("COMPARTIDO");
            comercio.setWaSincronizadoEn(LocalDateTime.now());
        }
    }

    private void contactosDeApp(Comercio comercio, JsonNode value) {
        for (JsonNode item : value.path("state_sync")) {
            if (!"contact".equals(texto(item.path("type")))) continue;
            if ("remove".equals(texto(item.path("action")))) continue;
            JsonNode contact = item.path("contact");
            String telefono = digitos(texto(contact.path("phone_number")));
            if (telefono == null) continue;
            String nombre = texto(contact.path("full_name"));
            if (nombre == null) nombre = texto(contact.path("first_name"));
            contactoDe(comercio, telefono, nombre);
        }
        comercio.setWaSincronizadoEn(LocalDateTime.now());
    }

    private void cuenta(JsonNode value, String wabaEntrada) {
        String evento = texto(value.path("event"));
        if (evento == null) return;
        String normal = evento.toUpperCase(Locale.ROOT);
        boolean corta = normal.contains("REMOVED") || normal.contains("OFFBOARD") || normal.contains("DELETED") || normal.contains("DISABLED") || normal.contains("REVOK");
        if (!corta) return;
        String waba = texto(value.path("waba_info").path("waba_id"));
        if (waba == null) waba = wabaEntrada;
        if (waba == null) return;
        comercios.findByWaWabaId(waba).ifPresent(this::marcarRevocado);
    }

    private boolean anotar(WhatsappContacto contacto, String direccion, String origen, String texto, boolean noLeido, String metaId, String estado, LocalDateTime cuando) {
        String cuerpo = texto == null ? "" : texto.trim();
        if (cuerpo.isEmpty()) return false;
        if (cuerpo.length() > 4000) cuerpo = cuerpo.substring(0, 4000);
        if (metaId != null && mensajes.findByMetaId(metaId).isPresent()) return false;
        WhatsappMensaje mensaje = new WhatsappMensaje();
        mensaje.setContacto(contacto);
        mensaje.setDireccion(direccion);
        mensaje.setOrigen(origen);
        mensaje.setTexto(cuerpo);
        mensaje.setMetaId(metaId);
        mensaje.setEstado(estado);
        mensaje.setCreadoEn(cuando == null ? LocalDateTime.now() : cuando);
        try {
            mensajes.saveAndFlush(mensaje);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return false;
        }
        contacto.setUltimoTexto(cuerpo.length() > 500 ? cuerpo.substring(0, 500) : cuerpo);
        if (contacto.getUltimoEn() == null || mensaje.getCreadoEn().isAfter(contacto.getUltimoEn())) contacto.setUltimoEn(mensaje.getCreadoEn());
        if (noLeido) contacto.setNoLeidos(contacto.getNoLeidos() + 1);
        else if ("SALIENTE".equals(direccion) && "LOCAL".equals(origen)) contacto.setNoLeidos(0);
        return true;
    }

    private String nombreDe(JsonNode value, String telefono) {
        for (JsonNode contacto : value.path("contacts")) {
            String id = digitos(texto(contacto.path("wa_id")));
            if (telefono.equals(id)) return texto(contacto.path("profile").path("name"));
        }
        return null;
    }

    private String digitos(String valor) {
        if (valor == null) return null;
        String limpio = valor.replaceAll("\\D", "");
        return limpio.isEmpty() ? null : limpio;
    }

    private String enviar(String phoneId, String token, String destino, String cuerpo) {
        if (token == null || token.isBlank()) throw new NegocioException("Conectá WhatsApp en Ajustes antes de escribir");
        String recorte = cuerpo.length() > 3500 ? cuerpo.substring(0, 3500) : cuerpo;
        Map<String, Object> text = Map.of("body", recorte, "preview_url", false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", destino);
        payload.put("type", "text");
        payload.put("text", text);
        try {
            JsonNode data = graph("POST", "/" + phoneId + "/messages", token, json.writeValueAsString(payload));
            return data.path("messages").isArray() && data.path("messages").size() > 0 ? texto(data.path("messages").get(0).path("id")) : null;
        } catch (TokenRevocadoException ex) {
            throw ex;
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("No se pudo enviar el mensaje");
        }
    }

    private JsonNode intercambiar(String code) {
        String consulta = "client_id=" + java.net.URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "&client_secret=" + java.net.URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
                + "&code=" + java.net.URLEncoder.encode(code, StandardCharsets.UTF_8);
        return pedir("GET", "/oauth/access_token?" + consulta, null, null);
    }

    private java.util.Set<String> cuentasDelToken(String token) {
        JsonNode data = pedir("GET", "/debug_token?input_token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8), appId + "|" + appSecret, null);
        java.util.LinkedHashSet<String> cuentas = new java.util.LinkedHashSet<>();
        for (JsonNode alcance : data.path("data").path("granular_scopes")) {
            if (!"whatsapp_business_management".equals(texto(alcance.path("scope")))) continue;
            for (JsonNode id : alcance.path("target_ids")) {
                String valor = texto(id);
                if (valor != null) cuentas.add(valor);
            }
        }
        return cuentas;
    }

    private boolean puedeEnviar(Comercio comercio) {
        if (comercio.getWaPhoneNumberId() == null) return false;
        String estado = comercio.getWaEstado();
        if ("DESCONECTADO".equals(estado) || "RECONEXION".equals(estado) || "ERROR".equals(estado)) return false;
        try {
            String token = tokenPlano(comercio);
            return token != null && !token.isBlank();
        } catch (NegocioException ex) {
            return false;
        }
    }

    private boolean puedeResponder(Comercio comercio) {
        return comercio.isWaActivo() && puedeEnviar(comercio);
    }

    private String cuerpoDe(JsonNode mensaje) {
        String tipo = texto(mensaje.path("type"));
        if ("text".equals(tipo)) return texto(mensaje.path("text").path("body"));
        if (tipo == null) return null;
        return "[" + tipo + "]";
    }

    private LocalDateTime epoch(JsonNode nodo) {
        String raw = texto(nodo);
        if (raw == null) return LocalDateTime.now();
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(raw)), ZoneId.systemDefault());
        } catch (RuntimeException ex) {
            return LocalDateTime.now();
        }
    }

    private int rango(String estado) {
        if (estado == null) return 0;
        return switch (estado) {
            case "ACEPTADO" -> 1;
            case "ENVIADO" -> 2;
            case "ENTREGADO" -> 3;
            case "LEIDO" -> 4;
            case "FALLIDO" -> 9;
            default -> 0;
        };
    }

    private JsonNode graph(String method, String path, String token, String cuerpo) {
        return pedir(method, path, token, cuerpo);
    }

    private JsonNode pedirForma(String path, String token, String cuerpo) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(graph + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode data = respuesta.body() == null || respuesta.body().isBlank() ? json.createObjectNode() : json.readTree(respuesta.body());
            if (respuesta.statusCode() >= 400) {
                String detalle = texto(data.path("error").path("message"));
                throw new NegocioException(detalle == null ? "Meta no aceptó la suscripción del webhook de Giro" : detalle);
            }
            return data;
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("No se pudo hablar con WhatsApp");
        }
    }

    private JsonNode pedir(String method, String path, String token, String cuerpo) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(graph + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpRequest request;
            if ("POST".equals(method)) {
                request = builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo == null ? "" : cuerpo, StandardCharsets.UTF_8))
                        .build();
            } else if ("DELETE".equals(method)) {
                request = builder.DELETE().build();
            } else {
                request = builder.GET().build();
            }
            HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode data = respuesta.body() == null || respuesta.body().isBlank() ? json.createObjectNode() : json.readTree(respuesta.body());
            if (respuesta.statusCode() >= 400) {
                int code = data.path("error").path("code").asInt(0);
                if (WhatsappFirma.esRevocacion(code)) throw new TokenRevocadoException();
                String detalle = texto(data.path("error").path("message"));
                throw new NegocioException(detalle == null ? "Meta no aceptó la conexión de Giro" : detalle);
            }
            return data;
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("No se pudo hablar con WhatsApp");
        }
    }

    private boolean contiene(String texto, String... claves) {
        for (String clave : claves) {
            if (texto.contains(clave)) return true;
        }
        return false;
    }

    private String normalizar(String valor) {
        String limpio = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return limpio.toLowerCase(Locale.ROOT);
    }

    private String texto(JsonNode nodo) {
        if (nodo == null || nodo.isMissingNode() || nodo.isNull()) return null;
        String valor = nodo.asText("").trim();
        return valor.isEmpty() ? null : valor;
    }

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
