package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.Comercio;
import com.giro.domain.Vidriera;
import com.giro.domain.WhatsappVinculo;
import com.giro.error.NegocioException;
import com.giro.repo.ComercioRepository;
import com.giro.repo.VidrieraRepository;
import com.giro.repo.WhatsappVinculoRepository;
import com.giro.seguridad.Sesion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AjustesService {
    static final List<String> CANALES = List.of("FISICA", "ONLINE", "AMBAS");
    static final List<String> OFERTAS = List.of("PRODUCTOS", "SERVICIOS", "AMBAS");
    static final List<String> DETALLES = List.of("talle", "calzado", "medidas", "peso", "marca", "modelo", "color", "material");

    private final Sesion sesion;
    private final ComercioRepository comercios;
    private final VidrieraRepository vidrieras;
    private final WhatsappGiroService whatsapp;
    private final WhatsappVinculoRepository vinculos;
    private final String urlPublica;

    public AjustesService(Sesion sesion, ComercioRepository comercios, VidrieraRepository vidrieras, WhatsappGiroService whatsapp,
                          WhatsappVinculoRepository vinculos,
                          @Value("${giro.public-url:}") String urlPublica) {
        this.sesion = sesion;
        this.comercios = comercios;
        this.vidrieras = vidrieras;
        this.whatsapp = whatsapp;
        this.vinculos = vinculos;
        this.urlPublica = urlPublica == null ? "" : urlPublica.trim().replaceAll("/$", "");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ver() {
        sesion.exigirGestion();
        return vista(comercio());
    }

    @Transactional
    public Map<String, Object> guardar(Modelos.AjustesEntrada entrada) {
        sesion.exigirGestion();
        Comercio comercio = comercio();
        String nombre = entrada.nombre().trim();
        if (nombre.isBlank()) throw new NegocioException("Completá el nombre del negocio");
        String anterior = comercio.getNombre();
        comercio.setNombre(nombre);
        comercio.setCalle(texto(entrada.calle(), 120, "La calle"));
        comercio.setAltura(texto(entrada.altura(), 16, "La altura"));
        comercio.setLocalidad(texto(entrada.localidad(), 80, "La localidad"));
        comercio.setPartido(texto(entrada.partido(), 80, "El partido"));
        comercio.setProvincia(Provincias.canon(entrada.provincia()));
        comercio.setCodigoPostal(Provincias.codigoPostal(entrada.codigoPostal()));
        comercio.setCanal(enumDe(entrada.canal(), CANALES, "Elegí si vendés en un local, online o en ambos"));
        comercio.setRubro(rubros(entrada.rubros()));
        String oferta = enumDe(entrada.oferta(), OFERTAS, "Elegí si vendés productos, servicios o ambos");
        comercio.setOferta(oferta);
        comercio.setDetallesProducto("SERVICIOS".equals(oferta) ? null : detalles(entrada.detalles()));
        comercio.setMpTitular(texto(entrada.mpTitular(), 160, "El titular"));
        comercio.setMpAlias(alias(entrada.mpAlias()));
        comercio.setMpCvu(cuenta(entrada.mpCvu(), "El CVU"));
        comercio.setMpCbu(cuenta(entrada.mpCbu(), "El CBU"));
        if (entrada.desconectarWhatsapp()) {
            whatsapp.desconectar(comercio);
        } else {
            String phone = telefonoMeta(entrada.waPhoneNumberId());
            if (phone != null) {
                comercios.findByWaPhoneNumberId(phone)
                        .filter(otro -> !otro.getId().equals(comercio.getId()))
                        .ifPresent(otro -> {
                            throw new NegocioException("Ese número de WhatsApp ya está conectado a otro comercio de Giro");
                        });
            }
            comercio.setWaPhoneNumberId(phone);
            comercio.setWaWabaId(telefonoMeta(entrada.waWabaId()));
            String token = tokenNuevo(entrada.waAccessToken());
            if (token != null) whatsapp.guardarToken(comercio, token);
            boolean activo = entrada.waActivo();
            if (activo && (comercio.getWaPhoneNumberId() == null || comercio.getWaAccessToken() == null || comercio.getWaAccessToken().isBlank())) {
                throw new NegocioException("Para activar el bot cargá el Phone Number ID y el Access Token de la app de Giro");
            }
            comercio.setWaActivo(activo);
        }
        comercio.setWaSaludo(texto(entrada.waSaludo(), 500, "El saludo"));
        if (!nombre.equals(anterior)) {
            vidrieras.findByComercioId(comercio.getId()).ifPresent(vidriera -> alinearTitulo(vidriera, anterior, nombre));
        }
        return vista(comercio);
    }

    @Transactional
    public Map<String, Object> probarWhatsapp(Modelos.ProbarWhatsapp entrada) {
        sesion.exigirGestion();
        Comercio comercio = comercio();
        String phone = entrada == null ? null : telefonoMeta(entrada.phoneNumberId());
        if (phone == null) phone = comercio.getWaPhoneNumberId();
        String token = entrada == null ? null : tokenNuevo(entrada.accessToken());
        if (token == null) token = whatsapp.tokenPlano(comercio);
        Map<String, Object> resultado = whatsapp.probar(phone, token);
        if (Boolean.TRUE.equals(resultado.get("ok")) && phone != null && phone.equals(comercio.getWaPhoneNumberId())) {
            Object numero = resultado.get("displayPhoneNumber");
            if (numero != null) comercio.setWaNumero(String.valueOf(numero));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> suscribirWhatsapp(Modelos.SuscribirWhatsapp entrada) {
        sesion.exigirGestion();
        Comercio comercio = comercio();
        String waba = entrada == null ? null : idMeta(entrada.wabaId());
        if (waba == null) waba = comercio.getWaWabaId();
        String token = entrada == null ? null : tokenNuevo(entrada.accessToken());
        if (token == null) token = whatsapp.tokenPlano(comercio);
        return whatsapp.suscribir(waba, token);
    }

    @Transactional
    public Map<String, Object> prepararWhatsapp() {
        sesion.exigirGestion();
        WhatsappVinculo vinculo = new WhatsappVinculo();
        vinculo.setComercioId(sesion.comercioId());
        vinculo.setClave(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        vinculo.setVenceEn(LocalDateTime.now().plusMinutes(10));
        vinculos.save(vinculo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", vinculo.getClave());
        body.put("venceEn", vinculo.getVenceEn().toString());
        return body;
    }

    @Transactional
    public Map<String, Object> conectarWhatsapp(Modelos.ConectarWhatsapp entrada) {
        sesion.exigirGestion();
        Comercio comercio = comercio();
        if (entrada == null || entrada.state() == null || entrada.state().isBlank()) {
            throw new NegocioException("Falta la verificación de la sesión");
        }
        if (entrada.code() == null || entrada.code().isBlank()) {
            throw new NegocioException("Meta no devolvió el código de conexión");
        }
        WhatsappVinculo vinculo = vinculos.findByClave(entrada.state().trim())
                .orElseThrow(() -> new NegocioException("La conexión con Meta venció. Volvé a empezar."));
        if (vinculo.isUsado() || !vinculo.getComercioId().equals(comercio.getId()) || vinculo.getVenceEn().isBefore(LocalDateTime.now())) {
            throw new NegocioException("La conexión con Meta venció. Volvé a empezar.");
        }
        vinculo.setUsado(true);
        String tipo = "COEXISTENCIA".equals(entrada.onboardingType()) ? "COEXISTENCIA" : "ESTANDAR";
        Map<String, Object> meta = whatsapp.conectar(entrada.code(), idMeta(entrada.phoneNumberId()), idMeta(entrada.wabaId()), tipo);
        String phone = String.valueOf(meta.get("phoneNumberId"));
        comercios.findByWaPhoneNumberId(phone)
                .filter(otro -> !otro.getId().equals(comercio.getId()))
                .ifPresent(otro -> {
                    throw new NegocioException("Ese número de WhatsApp ya está conectado a otro comercio de Giro");
                });
        comercio.setWaPhoneNumberId(phone);
        comercio.setWaWabaId(String.valueOf(meta.get("wabaId")));
        whatsapp.guardarToken(comercio, String.valueOf(meta.get("accessToken")));
        Object numero = meta.get("displayPhoneNumber");
        if (numero != null) comercio.setWaNumero(String.valueOf(numero));
        Object nombre = meta.get("verifiedName");
        if (nombre != null) comercio.setWaNombre(String.valueOf(nombre));
        comercio.setWaTipo(String.valueOf(meta.get("tipo")));
        comercio.setWaEstado("CONECTADO");
        comercio.setWaActivo(true);
        comercio.setWaConectadoEn(LocalDateTime.now());
        comercio.setWaRevocadoEn(null);
        if (meta.get("expiresIn") instanceof Number segundos && segundos.longValue() > 0) {
            comercio.setWaTokenVenceEn(LocalDateTime.now().plusSeconds(segundos.longValue()));
        } else {
            comercio.setWaTokenVenceEn(null);
        }
        comercio.setWaHistorial("COEXISTENCIA".equals(meta.get("tipo")) ? "PENDIENTE" : "NO_PEDIDO");
        comercio.setWaAviso(meta.get("avisoSuscripcion") == null ? null : String.valueOf(meta.get("avisoSuscripcion")));
        Map<String, Object> body = vista(comercio);
        body.put("webhookSuscripto", meta.get("webhookSuscripto"));
        body.put("sincronizacionPedida", meta.get("sincronizacionPedida"));
        return body;
    }

    private void alinearTitulo(Vidriera vidriera, String anterior, String nombre) {
        if (vidriera.getTitulo() == null || vidriera.getTitulo().isBlank() || vidriera.getTitulo().equals(anterior)) {
            vidriera.setTitulo(nombre);
        }
    }

    private Comercio comercio() {
        return comercios.findById(sesion.comercioId())
                .orElseThrow(() -> new NegocioException("No encontré el comercio"));
    }

    private Map<String, Object> vista(Comercio comercio) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", comercio.getNombre());
        body.put("slug", comercio.getSlug());
        body.put("calle", comercio.getCalle());
        body.put("altura", comercio.getAltura());
        body.put("localidad", comercio.getLocalidad());
        body.put("partido", comercio.getPartido());
        body.put("provincia", comercio.getProvincia());
        body.put("codigoPostal", comercio.getCodigoPostal());
        body.put("canal", comercio.getCanal() == null ? "FISICA" : comercio.getCanal());
        body.put("rubros", leerRubros(comercio.getRubro()));
        body.put("oferta", comercio.getOferta() == null ? "PRODUCTOS" : comercio.getOferta());
        body.put("detalles", lista(comercio.getDetallesProducto()));
        body.put("mpTitular", comercio.getMpTitular());
        body.put("mpAlias", comercio.getMpAlias());
        body.put("mpCvu", comercio.getMpCvu());
        body.put("mpCbu", comercio.getMpCbu());
        body.put("mensajeTransferencia", whatsapp.textoTransferencia(comercio));
        body.put("waPhoneNumberId", comercio.getWaPhoneNumberId());
        body.put("waWabaId", comercio.getWaWabaId());
        body.put("waTokenEnmascarado", whatsapp.enmascarar(comercio));
        body.put("waNumero", comercio.getWaNumero());
        body.put("waNombre", comercio.getWaNombre());
        body.put("waSaludo", comercio.getWaSaludo());
        body.put("waActivo", comercio.isWaActivo());
        body.put("waTipo", comercio.getWaTipo());
        body.put("waEstado", estadoWhatsapp(comercio));
        body.put("waHistorial", comercio.getWaHistorial());
        body.put("waAviso", comercio.getWaAviso());
        body.put("waConectado", "CONECTADO".equals(estadoWhatsapp(comercio)));
        body.put("webhookUrl", urlPublica.isBlank() ? null : urlPublica + "/api/public/whatsapp");
        body.put("webhookRuta", "/api/public/whatsapp");
        body.put("metaAppId", whatsapp.appId().isBlank() ? null : whatsapp.appId());
        body.put("metaConfigId", whatsapp.configId().isBlank() ? null : whatsapp.configId());
        return body;
    }

    private List<String> lista(String guardado) {
        if (guardado == null || guardado.isBlank()) return List.of();
        List<String> pedidos = List.of(guardado.split(","));
        List<String> elegidos = new ArrayList<>();
        for (String codigo : DETALLES) {
            if (pedidos.contains(codigo)) elegidos.add(codigo);
        }
        return elegidos;
    }

    private String rubros(List<String> valores) {
        List<String> limpios = new ArrayList<>();
        if (valores != null) {
            for (String valor : valores) {
                if (valor == null || valor.isBlank()) continue;
                String limpio = valor.trim().replace("|", " ");
                if (limpio.length() > 40) throw new NegocioException("El rubro es demasiado largo");
                boolean repetido = limpios.stream().anyMatch(item -> item.equalsIgnoreCase(limpio));
                if (!repetido) limpios.add(limpio);
            }
        }
        if (limpios.size() > 12) throw new NegocioException("Podés elegir hasta 12 rubros");
        return limpios.isEmpty() ? null : String.join("|", limpios);
    }

    private List<String> leerRubros(String guardado) {
        if (guardado == null || guardado.isBlank()) return List.of();
        List<String> lista = new ArrayList<>();
        for (String parte : guardado.split("\\|")) {
            if (!parte.isBlank()) lista.add(parte.trim());
        }
        return lista;
    }

    private String detalles(List<String> valores) {
        if (valores == null || valores.isEmpty()) return null;
        List<String> elegidos = new ArrayList<>();
        for (String codigo : DETALLES) {
            if (valores.contains(codigo)) elegidos.add(codigo);
        }
        return elegidos.isEmpty() ? null : String.join(",", elegidos);
    }

    private String enumDe(String valor, List<String> validos, String mensaje) {
        if (valor == null) throw new NegocioException(mensaje);
        String limpio = valor.trim().toUpperCase(Locale.ROOT);
        if (!validos.contains(limpio)) throw new NegocioException(mensaje);
        return limpio;
    }

    private String texto(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (limpio.length() > maximo) throw new NegocioException(campo + " es demasiado largo");
        return limpio;
    }

    private String alias(String valor) {
        String limpio = texto(valor, 80, "El alias");
        if (limpio == null) return null;
        if (!limpio.matches("[A-Za-z0-9.]{6,80}")) {
            throw new NegocioException("El alias de Mercado Pago usa letras, números y puntos");
        }
        return limpio.toLowerCase(Locale.ROOT);
    }

    private String cuenta(String valor, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.replaceAll("\\s", "");
        if (!limpio.matches("\\d{22}")) throw new NegocioException(campo + " tiene que tener 22 números");
        return limpio;
    }

    private String idMeta(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return telefonoMeta(valor);
    }

    private String telefonoMeta(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (!limpio.matches("\\d{6,32}")) throw new NegocioException("El identificador de WhatsApp tiene que ser solo números");
        return limpio;
    }

    private String tokenNuevo(String valor) {
        if (valor == null || valor.isBlank() || valor.startsWith("***")) return null;
        String limpio = valor.trim();
        if (limpio.length() < 20) throw new NegocioException("El Access Token de WhatsApp está incompleto");
        if (limpio.length() > 2000) throw new NegocioException("El Access Token de WhatsApp es demasiado largo");
        return limpio;
    }

    private String estadoWhatsapp(Comercio comercio) {
        if (comercio.getWaEstado() != null && !comercio.getWaEstado().isBlank()) return comercio.getWaEstado();
        if (comercio.getWaPhoneNumberId() != null && comercio.getWaAccessToken() != null && !comercio.getWaAccessToken().isBlank()) return "CONECTADO";
        return "DESCONECTADO";
    }
}
