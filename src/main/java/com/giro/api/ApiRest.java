package com.giro.api;

import com.giro.domain.ImagenProducto;
import com.giro.domain.Producto;
import com.giro.domain.Vidriera;
import com.giro.error.NegocioException;
import com.giro.repo.ImagenProductoRepository;
import com.giro.repo.ProductoRepository;
import com.giro.repo.VidrieraRepository;
import com.giro.seguridad.Sesion;
import com.giro.servicio.AjustesService;
import com.giro.servicio.ArchivoService;
import com.giro.servicio.AuthService;
import com.giro.servicio.CatalogoService;
import com.giro.servicio.ComercialService;
import com.giro.servicio.EnvioService;
import com.giro.servicio.TableroService;
import com.giro.servicio.WhatsappBandejaService;
import com.giro.servicio.WhatsappGiroService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ApiRest {
    private ApiRest() {}

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthApi {
        private final AuthService auth;
        public AuthApi(AuthService auth) { this.auth = auth; }

        @PostMapping("/registro")
        public Map<String, Object> registro(@Valid @RequestBody Modelos.Registro entrada) { return auth.registrar(entrada); }

        @PostMapping("/login")
        public Map<String, Object> login(@Valid @RequestBody Modelos.Login entrada) { return auth.login(entrada); }

        @PostMapping("/refresh")
        public Map<String, Object> refresh() { return auth.renovar(); }

        @GetMapping("/yo")
        public Map<String, Object> yo() { return auth.yo(); }
    }

    @RestController
    @RequestMapping("/api/equipo")
    public static class EquipoApi {
        private final AuthService auth;
        public EquipoApi(AuthService auth) { this.auth = auth; }

        @GetMapping
        public Object listar() { return auth.equipo(); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.Empleado entrada) { return auth.crearEmpleado(entrada); }
    }

    @RestController
    @RequestMapping("/api/categorias")
    public static class CategoriaApi {
        private final CatalogoService catalogo;
        public CategoriaApi(CatalogoService catalogo) { this.catalogo = catalogo; }

        @GetMapping
        public List<Map<String, Object>> listar() { return catalogo.listarCategorias(); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.CategoriaEntrada entrada) { return catalogo.crearCategoria(entrada); }
    }

    @RestController
    @RequestMapping("/api/productos")
    public static class ProductoApi {
        private final CatalogoService catalogo;
        private final ArchivoService archivos;
        public ProductoApi(CatalogoService catalogo, ArchivoService archivos) {
            this.catalogo = catalogo;
            this.archivos = archivos;
        }

        @GetMapping
        public List<Map<String, Object>> listar() { return catalogo.listarProductos(); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.ProductoEntrada entrada) { return catalogo.crearProducto(entrada); }

        @PutMapping("/{id}")
        public Map<String, Object> editar(@PathVariable Long id, @Valid @RequestBody Modelos.ProductoEntrada entrada) {
            return catalogo.editarProducto(id, entrada);
        }

        @PostMapping("/{id}/desactivar")
        public void desactivar(@PathVariable Long id) { catalogo.desactivarProducto(id); }

        @PostMapping("/{id}/variantes")
        public Map<String, Object> variante(@PathVariable Long id, @Valid @RequestBody Modelos.VarianteEntrada entrada) {
            return catalogo.crearVariante(id, entrada);
        }

        @PostMapping("/{id}/promociones")
        public Map<String, Object> promo(@PathVariable Long id, @Valid @RequestBody Modelos.PromoEntrada entrada) {
            return catalogo.crearPromocion(id, entrada);
        }

        @PostMapping(value = "/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Map<String, Object> imagen(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
            return catalogo.subirImagen(id, archivo);
        }

        @GetMapping("/{productoId}/imagenes/{imagenId}")
        public ResponseEntity<?> foto(@PathVariable Long productoId, @PathVariable Long imagenId) throws Exception {
            return servir(catalogo.archivoPropio(productoId, imagenId), archivos);
        }
    }

    @RestController
    @RequestMapping("/api/clientes")
    public static class ClienteApi {
        private final CatalogoService catalogo;
        private final ComercialService comercial;
        public ClienteApi(CatalogoService catalogo, ComercialService comercial) {
            this.catalogo = catalogo;
            this.comercial = comercial;
        }

        @GetMapping
        public List<Map<String, Object>> listar() { return catalogo.listarClientes(); }

        @GetMapping("/{id}")
        public Map<String, Object> ficha(@PathVariable Long id) { return catalogo.fichaCliente(id); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.ClienteEntrada entrada) { return catalogo.crearCliente(entrada); }

        @PutMapping("/{id}")
        public Map<String, Object> editar(@PathVariable Long id, @Valid @RequestBody Modelos.ClienteEntrada entrada) {
            return catalogo.editarCliente(id, entrada);
        }

        @PostMapping("/{id}/cobros")
        public Map<String, Object> cobrar(@PathVariable Long id, @Valid @RequestBody Modelos.Cobro entrada) {
            return comercial.cobrarSaldo(id, entrada);
        }
    }

    @RestController
    @RequestMapping("/api/ventas")
    public static class VentaApi {
        private final ComercialService comercial;
        public VentaApi(ComercialService comercial) { this.comercial = comercial; }

        @GetMapping
        public List<Map<String, Object>> listar() { return comercial.listarVentas(); }

        @GetMapping("/{id}")
        public Map<String, Object> ver(@PathVariable Long id) { return comercial.verVenta(id); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.VentaEntrada entrada) { return comercial.vender(entrada); }

        @PostMapping("/{id}/anular")
        public Map<String, Object> anular(@PathVariable Long id) { return comercial.anular(id); }
    }

    @RestController
    @RequestMapping("/api/pedidos")
    public static class PedidoApi {
        private final ComercialService comercial;
        public PedidoApi(ComercialService comercial) { this.comercial = comercial; }

        @GetMapping
        public List<Map<String, Object>> listar() { return comercial.listarPedidos(); }

        @PostMapping
        public Map<String, Object> crear(@Valid @RequestBody Modelos.PedidoEntrada entrada) { return comercial.crearPedido(entrada); }

        @PostMapping("/{id}/cancelar")
        public Map<String, Object> cancelar(@PathVariable Long id) { return comercial.cancelarPedido(id); }

        @PostMapping("/{id}/confirmar")
        public Map<String, Object> confirmar(@PathVariable Long id, @Valid @RequestBody Modelos.ConfirmarPedido entrada) {
            return comercial.confirmarPedido(id, entrada);
        }
    }

    @RestController
    @RequestMapping("/api/envios")
    public static class EnvioApi {
        private final EnvioService envios;
        public EnvioApi(EnvioService envios) { this.envios = envios; }

        @GetMapping
        public Map<String, Object> ver() { return envios.ver(); }

        @PutMapping
        public Map<String, Object> guardar(@Valid @RequestBody Modelos.TarifaEnvio entrada) { return envios.guardar(entrada); }

        @PostMapping("/cotizar")
        public Map<String, Object> cotizar(@RequestBody Modelos.CotizarEnvio entrada) { return envios.cotizar(entrada); }
    }

    @RestController
    @RequestMapping("/api/caja")
    public static class CajaApi {
        private final ComercialService comercial;
        public CajaApi(ComercialService comercial) { this.comercial = comercial; }

        @GetMapping
        public Map<String, Object> actual() { return comercial.cajaActual(); }

        @GetMapping("/cierres")
        public List<Map<String, Object>> cierres() { return comercial.cierres(); }

        @GetMapping("/historial")
        public Map<String, Object> historial(@RequestParam(required = false) Integer anio,
                                              @RequestParam(required = false) Integer mes,
                                              @RequestParam(required = false) Integer dia,
                                              @RequestParam(required = false) Long clienteId,
                                              @RequestParam(required = false) Long productoId,
                                              @RequestParam(required = false) String medio,
                                              @RequestParam(required = false) String tipo,
                                              @RequestParam(required = false) String q) {
            return comercial.historial(anio, mes, dia, clienteId, productoId, medio, tipo, q);
        }

        @GetMapping(value = "/historial.pdf", produces = "application/pdf")
        public ResponseEntity<byte[]> pdf(@RequestParam(required = false) Integer anio,
                                           @RequestParam(required = false) Integer mes,
                                           @RequestParam(required = false) Integer dia,
                                           @RequestParam(required = false) Long clienteId,
                                           @RequestParam(required = false) Long productoId,
                                           @RequestParam(required = false) String medio,
                                           @RequestParam(required = false) String tipo,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String alcance) {
            byte[] cuerpo = comercial.arqueoPdf(anio, mes, dia, clienteId, productoId, medio, tipo, q, alcance);
            String nombre = "arqueo-" + (anio == null ? "hoy" : anio) + (mes == null ? "" : "-" + mes) + (dia == null ? "" : "-" + dia) + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(cuerpo);
        }

        @PostMapping("/abrir")
        public Map<String, Object> abrir(@Valid @RequestBody Modelos.AbrirCaja entrada) { return comercial.abrirCaja(entrada); }

        @PostMapping("/movimientos")
        public Map<String, Object> movimiento(@Valid @RequestBody Modelos.MovimientoCajaEntrada entrada) { return comercial.movimiento(entrada); }

        @PostMapping("/cerrar")
        public Map<String, Object> cerrar(@Valid @RequestBody Modelos.CerrarCaja entrada) { return comercial.cerrarCaja(entrada); }
    }

    @RestController
    @RequestMapping("/api/dashboard")
    public static class TableroApi {
        private final TableroService tablero;
        public TableroApi(TableroService tablero) { this.tablero = tablero; }

        @GetMapping
        public Map<String, Object> resumen() { return tablero.resumen(); }

        @GetMapping("/analitica")
        public Map<String, Object> analitica(@RequestParam LocalDate desde, @RequestParam LocalDate hasta) {
            return tablero.analitica(desde, hasta);
        }
    }

    @RestController
    @RequestMapping("/api/vidriera")
    public static class VidrieraApi {
        private final CatalogoService catalogo;
        public VidrieraApi(CatalogoService catalogo) { this.catalogo = catalogo; }

        @GetMapping
        public Map<String, Object> ver() { return catalogo.verVidriera(); }

        @PutMapping
        public Map<String, Object> guardar(@Valid @RequestBody Modelos.VidrieraEntrada entrada) { return catalogo.guardarVidriera(entrada); }

        @PostMapping(value = "/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Map<String, Object> portada(@RequestPart("archivo") MultipartFile archivo) { return catalogo.subirPortada(archivo); }

        @PostMapping("/portada/quitar")
        public Map<String, Object> quitarPortada(@RequestBody(required = false) java.util.Map<String, String> cuerpo) {
            return catalogo.quitarPortada(cuerpo == null ? null : cuerpo.get("url"));
        }

        @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Map<String, Object> logo(@RequestPart("archivo") MultipartFile archivo) { return catalogo.subirLogo(archivo); }

        @PostMapping("/logo/quitar")
        public Map<String, Object> quitarLogo() { return catalogo.quitarLogo(); }
    }

    @RestController
    @RequestMapping("/api/public/vidrieras")
    public static class PublicoApi {
        private final VidrieraRepository vidrieras;
        private final ProductoRepository productos;
        private final ImagenProductoRepository imagenes;
        private final CatalogoService catalogo;
        private final ArchivoService archivos;

        public PublicoApi(VidrieraRepository vidrieras, ProductoRepository productos, ImagenProductoRepository imagenes,
                          CatalogoService catalogo, ArchivoService archivos) {
            this.vidrieras = vidrieras;
            this.productos = productos;
            this.imagenes = imagenes;
            this.catalogo = catalogo;
            this.archivos = archivos;
        }

        @GetMapping("/{slug}")
        public Map<String, Object> ver(@PathVariable String slug) {
            return catalogo.vidrieraPublica(slug);
        }

        @GetMapping("/{slug}/imagenes/{imagenId}")
        public ResponseEntity<?> foto(@PathVariable String slug, @PathVariable Long imagenId) throws Exception {
            ImagenProducto imagen = catalogo.imagenPublica(slug, imagenId);
            return servir(imagen.getArchivo(), archivos);
        }
    }

    @RestController
    @RequestMapping("/api/ajustes")
    public static class AjustesApi {
        private final AjustesService ajustes;
        public AjustesApi(AjustesService ajustes) { this.ajustes = ajustes; }

        @GetMapping
        public Map<String, Object> ver() { return ajustes.ver(); }

        @PutMapping
        public Map<String, Object> guardar(@Valid @RequestBody Modelos.AjustesEntrada entrada) { return ajustes.guardar(entrada); }

        @PostMapping("/whatsapp/probar")
        public Map<String, Object> probar(@RequestBody(required = false) Modelos.ProbarWhatsapp entrada) {
            return ajustes.probarWhatsapp(entrada);
        }

        @PostMapping("/whatsapp/suscribir")
        public Map<String, Object> suscribir(@RequestBody(required = false) Modelos.SuscribirWhatsapp entrada) {
            return ajustes.suscribirWhatsapp(entrada);
        }

        @PostMapping("/whatsapp/preparar")
        public Map<String, Object> prepararWhatsapp() { return ajustes.prepararWhatsapp(); }

        @PostMapping("/whatsapp/sincronizar")
        public Map<String, Object> sincronizarWhatsapp() { return ajustes.sincronizarWhatsapp(); }

        @PostMapping("/whatsapp/conectar")
        public Map<String, Object> conectar(@RequestBody Modelos.ConectarWhatsapp entrada) { return ajustes.conectarWhatsapp(entrada); }
    }

    @RestController
    @RequestMapping("/api/whatsapp")
    public static class WhatsappBandejaApi {
        private final WhatsappBandejaService bandeja;
        public WhatsappBandejaApi(WhatsappBandejaService bandeja) { this.bandeja = bandeja; }

        @GetMapping("/conversaciones")
        public List<Map<String, Object>> conversaciones() { return bandeja.conversaciones(); }

        @GetMapping("/conversaciones/{id}")
        public Map<String, Object> conversacion(@PathVariable Long id) { return bandeja.conversacion(id); }

        @PostMapping("/conversaciones/{id}/mensajes")
        public Map<String, Object> enviar(@PathVariable Long id, @Valid @RequestBody Modelos.TextoWhatsapp entrada) {
            return bandeja.enviar(id, entrada);
        }

        @PostMapping("/conversaciones/{id}/bot")
        public Map<String, Object> bot(@PathVariable Long id, @RequestBody Modelos.BotWhatsapp entrada) {
            return bandeja.bot(id, entrada != null && entrada.activo());
        }
    }

    @RestController
    @RequestMapping({"/api/public/whatsapp", "/api/public/whatsapp/"})
    public static class WhatsappPublicoApi {
        private final WhatsappGiroService whatsapp;
        private final com.giro.servicio.WhatsappCola cola;
        public WhatsappPublicoApi(WhatsappGiroService whatsapp, com.giro.servicio.WhatsappCola cola) {
            this.whatsapp = whatsapp;
            this.cola = cola;
        }

        @GetMapping
        public ResponseEntity<String> verificar(jakarta.servlet.http.HttpServletRequest request) {
            return whatsapp.verificar(
                    request.getParameter("hub.mode"),
                    request.getParameter("hub.verify_token"),
                    request.getParameter("hub.challenge"));
        }

        @PostMapping
        public ResponseEntity<Void> recibir(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
            byte[] raw = request.getInputStream().readAllBytes();
            if (!cola.aceptar(raw, request.getHeader("X-Hub-Signature-256"))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok().build();
        }
    }

    private static ResponseEntity<?> servir(String guardado, ArchivoService archivos) throws Exception {
        if (guardado.startsWith("https://") || guardado.startsWith("http://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(guardado)).build();
        }
        return archivo(archivos.resolver(guardado));
    }

    private static ResponseEntity<FileSystemResource> archivo(Path path) throws Exception {
        String tipo = Files.probeContentType(path);
        MediaType media = tipo == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(tipo);
        return ResponseEntity.ok().contentType(media).body(new FileSystemResource(path));
    }
}
