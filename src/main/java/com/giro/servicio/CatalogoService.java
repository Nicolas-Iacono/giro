package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.Categoria;
import com.giro.domain.Cliente;
import com.giro.domain.ImagenProducto;
import com.giro.domain.Producto;
import com.giro.domain.Promocion;
import com.giro.domain.Variante;
import com.giro.domain.Vidriera;
import com.giro.error.NegocioException;
import com.giro.repo.CategoriaRepository;
import com.giro.repo.ClienteRepository;
import com.giro.repo.DetalleVentaRepository;
import com.giro.repo.ImagenProductoRepository;
import com.giro.repo.ProductoRepository;
import com.giro.repo.PromocionRepository;
import com.giro.repo.SuscripcionRepository;
import com.giro.repo.VarianteRepository;
import com.giro.repo.VentaRepository;
import com.giro.repo.VidrieraRepository;
import com.giro.seguridad.Sesion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogoService {
    private final Sesion sesion;
    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final VarianteRepository variantes;
    private final ImagenProductoRepository imagenes;
    private final PromocionRepository promociones;
    private final ClienteRepository clientes;
    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final VidrieraRepository vidrieras;
    private final SuscripcionRepository suscripciones;
    private final ArchivoService archivos;

    public CatalogoService(Sesion sesion, CategoriaRepository categorias, ProductoRepository productos,
                           VarianteRepository variantes, ImagenProductoRepository imagenes,
                           PromocionRepository promociones, ClienteRepository clientes, VentaRepository ventas,
                           DetalleVentaRepository detalles, VidrieraRepository vidrieras,
                           SuscripcionRepository suscripciones, ArchivoService archivos) {
        this.sesion = sesion;
        this.categorias = categorias;
        this.productos = productos;
        this.variantes = variantes;
        this.imagenes = imagenes;
        this.promociones = promociones;
        this.clientes = clientes;
        this.ventas = ventas;
        this.detalles = detalles;
        this.vidrieras = vidrieras;
        this.suscripciones = suscripciones;
        this.archivos = archivos;
    }

    @Transactional
    public Map<String, Object> crearCategoria(Modelos.CategoriaEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Categoria categoria = new Categoria();
        categoria.setComercio(sesion.usuario().getComercio());
        categoria.setNombre(entrada.nombre().trim());
        categorias.save(categoria);
        return Map.of("id", categoria.getId(), "nombre", categoria.getNombre());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarCategorias() {
        return categorias.findByComercioIdOrderByNombreAsc(sesion.comercioId()).stream()
                .map(c -> Map.<String, Object>of("id", c.getId(), "nombre", c.getNombre()))
                .toList();
    }

    @Transactional
    public Map<String, Object> crearProducto(Modelos.ProductoEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        var suscripcion = suscripciones.findByComercioId(sesion.comercioId()).orElseThrow();
        if (productos.countByComercioIdAndActivoTrue(sesion.comercioId()) >= suscripcion.getPlan().getMaxProductos()) {
            throw new NegocioException("El plan no admite más productos activos");
        }
        Producto producto = new Producto();
        producto.setComercio(sesion.usuario().getComercio());
        aplicar(producto, entrada, null);
        productos.save(producto);
        return vista(producto);
    }

    @Transactional
    public Map<String, Object> editarProducto(Long id, Modelos.ProductoEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Producto producto = propio(id);
        aplicar(producto, entrada, id);
        return vista(producto);
    }

    @Transactional
    public void desactivarProducto(Long id) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Producto producto = propio(id);
        if (detalles.existsByProductoId(id)) {
            producto.setActivo(false);
            producto.setPublicado(false);
            return;
        }
        promociones.deleteByProductoId(id);
        imagenes.deleteByProductoId(id);
        variantes.deleteByProductoId(id);
        productos.delete(producto);
    }

    @Transactional
    public Map<String, Object> crearVariante(Long productoId, Modelos.VarianteEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Producto producto = propio(productoId);
        producto.setUsaVariantes(true);
        String sku = Textos.sku(entrada.sku());
        if (sku != null && variantes.existsByProducto_Comercio_IdAndSku(sesion.comercioId(), sku)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ese SKU ya existe");
        }
        Variante variante = new Variante();
        variante.setProducto(producto);
        variante.setNombre(entrada.nombre().trim());
        variante.setSku(sku);
        variante.setPrecio(entrada.precio() == null ? null : Textos.dinero(entrada.precio(), "El precio"));
        variante.setStock(entrada.stock().setScale(3, java.math.RoundingMode.HALF_UP));
        variantes.save(variante);
        return vista(producto);
    }

    @Transactional
    public Map<String, Object> crearPromocion(Long productoId, Modelos.PromoEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Producto producto = propio(productoId);
        if (entrada.desde() != null && entrada.hasta() != null && entrada.hasta().isBefore(entrada.desde())) {
            throw new NegocioException("La fecha de fin es anterior al inicio");
        }
        Promocion promo = new Promocion();
        promo.setComercio(producto.getComercio());
        promo.setProducto(producto);
        promo.setPrecioPromo(Textos.dinero(entrada.precioPromo(), "El precio promocional"));
        promo.setDesde(entrada.desde());
        promo.setHasta(entrada.hasta());
        promociones.save(promo);
        return vista(producto);
    }

    @Transactional
    public Map<String, Object> subirImagen(Long productoId, MultipartFile file) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Producto producto = propio(productoId);
        if (imagenes.countByProductoId(productoId) >= 8) {
            throw new NegocioException("Un producto admite hasta 8 fotos");
        }
        ImagenProducto imagen = new ImagenProducto();
        imagen.setProducto(producto);
        imagen.setArchivo(archivos.guardar(sesion.comercioId(), file));
        imagen.setOrden(imagenes.countByProductoId(productoId));
        imagenes.save(imagen);
        return vista(producto);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarProductos() {
        return productos.findByComercioIdOrderByNombreAsc(sesion.comercioId()).stream().map(this::vista).toList();
    }

    public BigDecimal precioVigente(Producto producto, Variante variante) {
        BigDecimal base = producto.getPrecio();
        if (variante != null && variante.getPrecio() != null) {
            base = variante.getPrecio();
        }
        return promociones.vigentes(producto.getComercio().getId(), producto.getId(), LocalDate.now(Sesion.ZONA))
                .stream().findFirst().map(Promocion::getPrecioPromo).orElse(base);
    }

    @Transactional
    public Map<String, Object> crearCliente(Modelos.ClienteEntrada entrada) {
        sesion.exigirSuscripcion();
        Cliente cliente = new Cliente();
        cliente.setComercio(sesion.usuario().getComercio());
        copiarCliente(cliente, entrada);
        clientes.save(cliente);
        return vistaCliente(cliente, false);
    }

    @Transactional
    public Map<String, Object> editarCliente(Long id, Modelos.ClienteEntrada entrada) {
        sesion.exigirSuscripcion();
        Cliente cliente = clientePropio(id);
        copiarCliente(cliente, entrada);
        return vistaCliente(cliente, false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarClientes() {
        return clientes.findByComercioIdAndActivoTrueOrderByNombreAsc(sesion.comercioId()).stream()
                .map(c -> vistaCliente(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> fichaCliente(Long id) {
        return vistaCliente(clientePropio(id), true);
    }

    @Transactional(readOnly = true)
    public Vidriera vidrieraActual() {
        return vidrieras.findByComercioId(sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "El comercio no tiene vidriera"));
    }

    @Transactional
    public Map<String, Object> guardarVidriera(Modelos.VidrieraEntrada entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Vidriera vidriera = vidrieraActual();
        vidriera.setTitulo(entrada.titulo().trim());
        vidriera.setDescripcion(entrada.descripcion() == null ? "" : entrada.descripcion().trim());
        vidriera.setColor(colorHex(entrada.color()));
        vidriera.setPublicada(entrada.publicada());
        vidriera.setTema(opcion(entrada.tema(), TEMAS, "Elegí un tema de la vidriera"));
        vidriera.setFondo(opcion(entrada.fondo(), FONDOS, "Elegí el fondo de la vidriera"));
        vidriera.setTexto(opcion(entrada.texto(), TEXTOS, "Elegí el color del texto"));
        vidriera.setCatalogo(opcion(entrada.catalogo(), CATALOGOS, "Elegí cómo se ven las prendas"));
        if (entrada.radio() != 0 && entrada.radio() != 8 && entrada.radio() != 16) {
            throw new NegocioException("El redondeo de las tarjetas no es válido");
        }
        vidriera.setRadio(entrada.radio());
        vidriera.setCuotas(entrada.cuotas());
        String aviso = entrada.marquesina() == null ? "" : entrada.marquesina().trim();
        if (aviso.length() > 180) throw new NegocioException("El anuncio puede tener hasta 180 caracteres");
        vidriera.setMarquesina(aviso);
        vidriera.setHero(opcion(entrada.hero(), HEROES, "Elegí el estilo de la portada"));
        vidriera.setFuente(opcion(entrada.fuente(), FUENTES, "Elegí una tipografía"));
        String menu = entrada.menu() == null || entrada.menu().isBlank() ? "lados" : entrada.menu();
        vidriera.setMenu(opcion(menu, MENUS, "Elegí cómo se acomoda el menú"));
        String logoForma = entrada.logoForma() == null || entrada.logoForma().isBlank() ? "circular" : entrada.logoForma();
        vidriera.setLogoForma(opcion(logoForma, FORMAS, "Elegí la forma del logo"));
        if (entrada.mostrarRubros() != null) vidriera.setMostrarRubros(entrada.mostrarRubros());
        if (entrada.rubros() != null) vidriera.setRubros(rubrosValidos(entrada.rubros()));
        if (entrada.rubroForma() != null && !entrada.rubroForma().isBlank()) {
            vidriera.setRubroForma(opcion(entrada.rubroForma(), FORMAS_RUBRO, "Elegí la forma del fondo del ícono"));
        }
        if (entrada.rubroColor() != null) {
            vidriera.setRubroColor(entrada.rubroColor().isBlank() ? null : colorHex(entrada.rubroColor()));
        }
        if (entrada.secciones() != null) vidriera.setSecciones(seccionesValidas(entrada.secciones()));
        vidriera.setEstado(corto(entrada.estado(), 120, "La línea bajo el nombre puede tener hasta 120 caracteres"));
        vidriera.setEtiqueta(corto(entrada.etiqueta(), 80, "La etiqueta puede tener hasta 80 caracteres"));
        vidriera.setSello(corto(entrada.sello(), 80, "El sello puede tener hasta 80 caracteres"));
        vidriera.setHeroEtiqueta(corto(entrada.heroEtiqueta(), 80, "La etiqueta de la portada puede tener hasta 80 caracteres"));
        vidriera.setHeroTitulo(corto(entrada.heroTitulo(), 140, "El título de la portada puede tener hasta 140 caracteres"));
        vidriera.setHeroTexto(corto(entrada.heroTexto(), 240, "El texto de la portada puede tener hasta 240 caracteres"));
        vidriera.setHeroCta(corto(entrada.heroCta(), 40, "El botón de la portada puede tener hasta 40 caracteres"));
        vidriera.setBuscar(corto(entrada.buscar(), 80, "El texto del buscador puede tener hasta 80 caracteres"));
        vidriera.setAviso(corto(entrada.aviso(), 180, "El aviso puede tener hasta 180 caracteres"));
        vidriera.setExplorar(corto(entrada.explorar(), 60, "El título de categorías puede tener hasta 60 caracteres"));
        vidriera.setSumar(corto(entrada.sumar(), 24, "El botón de sumar puede tener hasta 24 caracteres"));
        String formaSumar = entrada.sumarForma() == null || entrada.sumarForma().isBlank() ? "circular" : entrada.sumarForma();
        vidriera.setSumarForma(opcion(formaSumar, FORMAS_SUMAR, "Elegí la forma del botón de sumar"));
        if (entrada.sumarColor() == null || entrada.sumarColor().isBlank()) vidriera.setSumarColor(null);
        else vidriera.setSumarColor(colorHex(entrada.sumarColor()));
        vidriera.setSumarVisible(entrada.sumarVisible() == null || entrada.sumarVisible());
        return vistaVidriera(vidriera);
    }

    @Transactional
    public Map<String, Object> subirPortada(org.springframework.web.multipart.MultipartFile archivo) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Vidriera vidriera = vidrieraActual();
        java.util.List<String> fotos = new java.util.ArrayList<>(portadasDe(vidriera));
        if (fotos.size() >= 8) throw new NegocioException("La portada acepta hasta 8 fotos");
        fotos.add(archivos.guardar(sesion.comercioId(), archivo));
        guardarPortadas(vidriera, fotos);
        vidriera.setHero("foto");
        return vistaVidriera(vidriera);
    }

    @Transactional
    public Map<String, Object> quitarPortada(String url) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Vidriera vidriera = vidrieraActual();
        java.util.List<String> fotos = new java.util.ArrayList<>(portadasDe(vidriera));
        if (url == null || url.isBlank()) fotos.clear();
        else fotos.removeIf(item -> item.equals(url));
        guardarPortadas(vidriera, fotos);
        return vistaVidriera(vidriera);
    }

    @Transactional
    public Map<String, Object> subirLogo(org.springframework.web.multipart.MultipartFile archivo) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Vidriera vidriera = vidrieraActual();
        vidriera.setLogoUrl(archivos.guardar(sesion.comercioId(), archivo));
        return vistaVidriera(vidriera);
    }

    @Transactional
    public Map<String, Object> quitarLogo() {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Vidriera vidriera = vidrieraActual();
        vidriera.setLogoUrl(null);
        return vistaVidriera(vidriera);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verVidriera() {
        return vistaVidriera(vidrieraActual());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> vidrieraPublica(String slug) {
        Vidriera vidriera = vidrieras.findByComercioSlug(slug)
                .filter(v -> v.isPublicada() && v.getComercio().isActivo())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Vidriera no encontrada"));
        List<Map<String, Object>> items = productos
                .findByComercioIdAndActivoTrueAndPublicadoTrueOrderByNombreAsc(vidriera.getComercio().getId())
                .stream().map(producto -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>(vista(producto));
                    item.remove("costo");
                    return item;
                }).toList();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("titulo", vidriera.getTitulo());
        body.put("descripcion", vidriera.getDescripcion() == null ? "" : vidriera.getDescripcion());
        body.put("color", vidriera.getColor());
        body.put("slug", vidriera.getComercio().getSlug());
        body.put("oferta", vidriera.getComercio().getOferta() == null ? "PRODUCTOS" : vidriera.getComercio().getOferta());
        body.put("detalles", detallesPublicos(vidriera.getComercio().getDetallesProducto()));
        cuerpoDiseno(body, vidriera);
        secciones(body, vidriera);
        body.put("productos", items);
        return body;
    }

    @Transactional(readOnly = true)
    public ImagenProducto imagenPublica(String slug, Long imagenId) {
        Vidriera vidriera = vidrieras.findByComercioSlug(slug)
                .filter(v -> v.isPublicada() && v.getComercio().isActivo())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Vidriera no encontrada"));
        ImagenProducto imagen = imagenes.findById(imagenId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
        Producto producto = imagen.getProducto();
        if (!producto.getComercio().getId().equals(vidriera.getComercio().getId()) || !producto.isPublicado() || !producto.isActivo()) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        return imagen;
    }

    @Transactional(readOnly = true)
    public String archivoPropio(Long productoId, Long imagenId) {
        propio(productoId);
        return imagenes.findByIdAndProductoId(imagenId, productoId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Imagen no encontrada"))
                .getArchivo();
    }

    public Producto propio(Long id) {
        return productos.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    public Cliente clientePropio(Long id) {
        return clientes.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    private void aplicar(Producto producto, Modelos.ProductoEntrada entrada, Long idActual) {
        String sku = Textos.sku(entrada.sku());
        Long comercioId = sesion.comercioId();
        boolean repetido = sku != null && (idActual == null
                ? productos.existsByComercioIdAndSku(comercioId, sku)
                : productos.existsByComercioIdAndSkuAndIdNot(comercioId, sku, idActual));
        if (repetido || (sku != null && variantes.existsByProducto_Comercio_IdAndSku(comercioId, sku))) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ese SKU ya existe");
        }
        producto.setNombre(entrada.nombre().trim());
        producto.setDescripcion(detalle(entrada.descripcion(), 2000));
        producto.setSku(sku);
        producto.setCosto(Textos.dinero(entrada.costo(), "El costo"));
        producto.setPrecio(Textos.dinero(entrada.precio(), "El precio"));
        producto.setStock(entrada.stock().setScale(3, java.math.RoundingMode.HALF_UP));
        producto.setStockMinimo(entrada.stockMinimo().setScale(3, java.math.RoundingMode.HALF_UP));
        producto.setPublicado(entrada.publicado());
        producto.setUsaVariantes(entrada.usaVariantes());
        producto.setPublico(publicoDe(entrada.publico()));
        producto.setMarca(detalle(entrada.marca(), 80));
        producto.setModelo(detalle(entrada.modelo(), 80));
        producto.setColor(detalle(entrada.color(), 40));
        producto.setMaterial(detalle(entrada.material(), 80));
        producto.setMedidas(detalle(entrada.medidas(), 80));
        producto.setPeso(detalle(entrada.peso(), 40));
        if (entrada.categoriaId() == null) {
            producto.setCategoria(null);
        } else {
            producto.setCategoria(categorias.findByIdAndComercioId(entrada.categoriaId(), comercioId)
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "La categoría no pertenece a este comercio")));
        }
    }

    private static final List<String> CATEGORIAS = List.of("ESTANDAR", "VIP", "MAYORISTA", "AMIGOS");
    private static final List<String> TALLES = List.of("XS", "S", "M", "L", "XL", "XXL");
    private static final List<String> PREFERENCIAS = List.of("BUZOS", "REMERAS", "PANTALONES", "ACCESORIOS", "CALZADO");
    private static final List<String> NUMEROS_CALZADO = List.of("35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46");
    private static final List<String> NUMEROS_PANTALON = List.of("30", "32", "34", "36", "38", "40", "42", "44", "46", "48", "50");
    private static final List<Integer> PLAZOS = List.of(15, 30, 45);

    private void copiarCliente(Cliente cliente, Modelos.ClienteEntrada entrada) {
        cliente.setNombre(entrada.nombre().trim());
        cliente.setTelefono(vacio(entrada.telefono()));
        cliente.setEmail(entrada.email() == null || entrada.email().isBlank() ? null : Textos.email(entrada.email()));
        cliente.setObservaciones(vacio(entrada.observaciones()));
        cliente.setDocumento(documento(entrada.documento()));
        cliente.setDireccion(texto(entrada.direccion(), 180, "La dirección"));
        cliente.setAltura(texto(entrada.altura(), 16, "La altura"));
        cliente.setLocalidad(texto(entrada.localidad(), 80, "La localidad"));
        cliente.setPartido(texto(entrada.partido(), 80, "El partido"));
        cliente.setProvincia(Provincias.canon(entrada.provincia()));
        cliente.setCodigoPostal(Provincias.codigoPostal(entrada.codigoPostal()));
        cliente.setCategoria(opcion(entrada.categoria(), CATEGORIAS, "ESTANDAR", "La categoría"));
        cliente.setAvisosWhatsapp(Boolean.TRUE.equals(entrada.avisosWhatsapp()));
        cliente.setCuentaCorriente(entrada.cuentaCorriente() == null || entrada.cuentaCorriente());
        cliente.setLimiteCredito(entrada.limiteCredito() == null ? null : Textos.dinero(entrada.limiteCredito(), "El límite de crédito"));
        cliente.setPlazoDias(plazo(entrada.plazoDias()));
        cliente.setAvisoVencimiento(Boolean.TRUE.equals(entrada.avisoVencimiento()));
        cliente.setTalle(talle(entrada.talle()));
        cliente.setTalleCalzado(numeroCalzado(entrada.talleCalzado()));
        cliente.setTallePantalon(numeroPantalon(entrada.tallePantalon()));
        cliente.setIntereses(intereses(entrada.intereses()));
    }

    private String documento(String valor) {
        String texto = vacio(valor);
        if (texto == null) return null;
        String limpio = texto.replace(" ", "");
        long digitos = limpio.chars().filter(Character::isDigit).count();
        if (digitos < 6 || digitos > 13 || !limpio.matches("[0-9.\\-]+")) {
            throw new NegocioException("El DNI o CUIT tiene que tener entre 6 y 13 números");
        }
        return limpio;
    }

    private String corto(String valor, int maximo, String mensaje) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.length() > maximo) throw new NegocioException(mensaje);
        return limpio;
    }

    private String texto(String valor, int maximo, String campo) {
        String limpio = vacio(valor);
        if (limpio != null && limpio.length() > maximo) {
            throw new NegocioException(campo + " es demasiado larga");
        }
        return limpio;
    }

    private String opcion(String valor, List<String> validos, String defecto, String campo) {
        String texto = vacio(valor);
        if (texto == null) return defecto;
        if (!validos.contains(texto)) throw new NegocioException(campo + " no es válida");
        return texto;
    }

    private String talle(String valor) {
        String texto = vacio(valor);
        if (texto == null) return null;
        if (!TALLES.contains(texto)) throw new NegocioException("El talle no es válido");
        return texto;
    }

    private String numeroCalzado(String valor) {
        String texto = vacio(valor);
        if (texto == null) return null;
        if (!NUMEROS_CALZADO.contains(texto)) throw new NegocioException("El número de calzado no es válido");
        return texto;
    }

    private String numeroPantalon(String valor) {
        String texto = vacio(valor);
        if (texto == null) return null;
        if (!NUMEROS_PANTALON.contains(texto)) throw new NegocioException("El número de pantalón no es válido");
        return texto;
    }

    private Integer plazo(Integer dias) {
        if (dias == null) return null;
        if (!PLAZOS.contains(dias)) throw new NegocioException("El plazo de pago tiene que ser 15, 30 o 45 días");
        return dias;
    }

    private String intereses(List<String> valores) {
        if (valores == null || valores.isEmpty()) return null;
        List<String> pedidos = valores.stream().filter(item -> item != null && !item.isBlank()).distinct().toList();
        List<String> elegidos = new ArrayList<>();
        for (String codigo : PREFERENCIAS) {
            if (pedidos.contains(codigo)) elegidos.add(codigo);
        }
        if (elegidos.size() != pedidos.size()) {
            throw new NegocioException("Hay una preferencia de prenda que no existe");
        }
        return elegidos.isEmpty() ? null : String.join(",", elegidos);
    }

    private List<String> interesesLista(String guardado) {
        if (guardado == null || guardado.isBlank()) return List.of();
        return List.of(guardado.split(","));
    }

    private String vacio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private List<String> detallesPublicos(String guardado) {
        if (guardado == null || guardado.isBlank()) return List.of();
        return List.of(guardado.split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String detalle(String valor, int maximo) {
        String limpio = vacio(valor);
        if (limpio == null) return null;
        if (limpio.length() > maximo) throw new NegocioException("Uno de los datos del producto es demasiado largo");
        return limpio;
    }

    public Map<String, Object> vista(Producto producto) {
        List<Variante> vars = variantes.findByProductoIdAndActivoTrueOrderByNombreAsc(producto.getId());
        List<ImagenProducto> fotos = imagenes.findByProductoIdOrderByOrdenAsc(producto.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", producto.getId());
        body.put("nombre", producto.getNombre());
        body.put("descripcion", producto.getDescripcion());
        body.put("sku", producto.getSku());
        body.put("categoriaId", producto.getCategoria() == null ? null : producto.getCategoria().getId());
        body.put("categoria", producto.getCategoria() == null ? null : producto.getCategoria().getNombre());
        body.put("costo", producto.getCosto());
        body.put("precio", producto.getPrecio());
        body.put("precioVigente", precioVigente(producto, null));
        body.put("stock", producto.getStock());
        body.put("stockMinimo", producto.getStockMinimo());
        body.put("publicado", producto.isPublicado());
        body.put("activo", producto.isActivo());
        body.put("usaVariantes", producto.isUsaVariantes());
        body.put("publico", producto.getPublico());
        body.put("marca", producto.getMarca());
        body.put("modelo", producto.getModelo());
        body.put("color", producto.getColor());
        body.put("material", producto.getMaterial());
        body.put("medidas", producto.getMedidas());
        body.put("peso", producto.getPeso());
        body.put("variantes", vars.stream().map(v -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", v.getId());
            item.put("nombre", v.getNombre());
            item.put("sku", v.getSku());
            item.put("precio", v.getPrecio());
            item.put("stock", v.getStock());
            return item;
        }).toList());
        body.put("imagenes", fotos.stream().map(ImagenProducto::getId).toList());
        body.put("fotos", fotos.stream().map(img -> {
            Map<String, Object> foto = new LinkedHashMap<>();
            foto.put("id", img.getId());
            String guardado = img.getArchivo();
            foto.put("url", guardado.startsWith("http://") || guardado.startsWith("https://") ? guardado : null);
            return foto;
        }).toList());
        return body;
    }

    private Map<String, Object> vistaCliente(Cliente cliente, boolean conHistorial) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", cliente.getId());
        body.put("nombre", cliente.getNombre());
        body.put("telefono", cliente.getTelefono());
        body.put("email", cliente.getEmail());
        body.put("observaciones", cliente.getObservaciones());
        body.put("documento", cliente.getDocumento());
        body.put("direccion", cliente.getDireccion());
        body.put("altura", cliente.getAltura());
        body.put("localidad", cliente.getLocalidad());
        body.put("partido", cliente.getPartido());
        body.put("provincia", cliente.getProvincia());
        body.put("codigoPostal", cliente.getCodigoPostal());
        body.put("categoria", cliente.getCategoria());
        body.put("avisosWhatsapp", cliente.isAvisosWhatsapp());
        body.put("cuentaCorriente", cliente.isCuentaCorriente());
        body.put("limiteCredito", cliente.getLimiteCredito());
        body.put("plazoDias", cliente.getPlazoDias());
        body.put("avisoVencimiento", cliente.isAvisoVencimiento());
        body.put("talle", cliente.getTalle());
        body.put("talleCalzado", cliente.getTalleCalzado());
        body.put("tallePantalon", cliente.getTallePantalon());
        body.put("intereses", interesesLista(cliente.getIntereses()));
        body.put("saldo", cliente.getSaldo());
        if (conHistorial) {
            body.put("compras", ventas.findByComercioIdAndClienteIdOrderByCreadaEnDesc(sesion.comercioId(), cliente.getId())
                    .stream().map(v -> Map.<String, Object>of(
                            "id", v.getId(),
                            "total", v.getTotal(),
                            "estado", v.getEstado().name(),
                            "fecha", v.getCreadaEn().toString()))
                    .toList());
        }
        return body;
    }

    private Map<String, Object> vistaVidriera(Vidriera vidriera) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titulo", vidriera.getTitulo());
        body.put("descripcion", vidriera.getDescripcion());
        body.put("color", vidriera.getColor());
        body.put("publicada", vidriera.isPublicada());
        body.put("slug", vidriera.getComercio().getSlug());
        body.put("localidad", vidriera.getComercio().getLocalidad());
        body.put("partido", vidriera.getComercio().getPartido());
        cuerpoDiseno(body, vidriera);
        secciones(body, vidriera);
        return body;
    }

    private static void cuerpoDiseno(Map<String, Object> body, Vidriera vidriera) {
        body.put("tema", vidriera.getTema() == null ? "giro" : vidriera.getTema());
        body.put("fondo", vidriera.getFondo() == null ? "blanco" : vidriera.getFondo());
        body.put("texto", vidriera.getTexto() == null ? "oscuro" : vidriera.getTexto());
        body.put("catalogo", vidriera.getCatalogo() == null ? "grilla" : vidriera.getCatalogo());
        body.put("radio", vidriera.getRadio());
        body.put("cuotas", vidriera.isCuotas());
        body.put("marquesina", vidriera.getMarquesina() == null ? "" : vidriera.getMarquesina());
        body.put("hero", vidriera.getHero() == null ? "gradiente" : vidriera.getHero());
        body.put("fuente", vidriera.getFuente() == null ? "jakarta" : vidriera.getFuente());
        body.put("portadaUrl", vidriera.getPortadaUrl());
        body.put("logoUrl", vidriera.getLogoUrl());
        body.put("menu", vidriera.getMenu() == null ? "lados" : vidriera.getMenu());
        body.put("logoForma", vidriera.getLogoForma() == null ? "circular" : vidriera.getLogoForma());
        body.put("estado", vidriera.getEstado() == null ? "" : vidriera.getEstado());
        body.put("etiqueta", vidriera.getEtiqueta() == null ? "" : vidriera.getEtiqueta());
        body.put("sello", vidriera.getSello() == null ? "" : vidriera.getSello());
        body.put("heroEtiqueta", vidriera.getHeroEtiqueta() == null ? "" : vidriera.getHeroEtiqueta());
        body.put("heroTitulo", vidriera.getHeroTitulo() == null ? "" : vidriera.getHeroTitulo());
        body.put("heroTexto", vidriera.getHeroTexto() == null ? "" : vidriera.getHeroTexto());
        body.put("heroCta", vidriera.getHeroCta() == null ? "" : vidriera.getHeroCta());
        body.put("buscar", vidriera.getBuscar() == null ? "" : vidriera.getBuscar());
        body.put("aviso", vidriera.getAviso() == null ? "" : vidriera.getAviso());
        body.put("explorar", vidriera.getExplorar() == null ? "" : vidriera.getExplorar());
        body.put("sumar", vidriera.getSumar() == null ? "" : vidriera.getSumar());
        body.put("sumarForma", vidriera.getSumarForma() == null ? "circular" : vidriera.getSumarForma());
        body.put("sumarColor", vidriera.getSumarColor() == null ? "" : vidriera.getSumarColor());
        body.put("sumarVisible", vidriera.isSumarVisible());
        body.put("localidad", vidriera.getComercio().getLocalidad());
        body.put("partido", vidriera.getComercio().getPartido());
    }

    private void secciones(Map<String, Object> body, Vidriera vidriera) {
        body.put("portadas", portadasDe(vidriera));
        body.put("mostrarRubros", vidriera.isMostrarRubros());
        body.put("rubroForma", vidriera.getRubroForma() == null ? "circular" : vidriera.getRubroForma());
        body.put("rubroColor", vidriera.getRubroColor());
        body.put("rubros", rubrosVisibles(vidriera));
        body.put("secciones", seccionesVisibles(vidriera));
    }

    private java.util.List<String> portadasDe(Vidriera vidriera) {
        String texto = vidriera.getPortadas();
        if (texto != null && !texto.isBlank()) {
            return java.util.Arrays.stream(texto.split("\n")).map(String::trim).filter(item -> !item.isEmpty()).toList();
        }
        if (vidriera.getPortadaUrl() != null && !vidriera.getPortadaUrl().isBlank()) return java.util.List.of(vidriera.getPortadaUrl());
        return java.util.List.of();
    }

    private void guardarPortadas(Vidriera vidriera, java.util.List<String> fotos) {
        vidriera.setPortadas(fotos.isEmpty() ? null : String.join("\n", fotos));
        vidriera.setPortadaUrl(fotos.isEmpty() ? null : fotos.get(0));
    }

    private String rubrosValidos(String texto) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String parte : texto.split(",")) {
            String limpio = parte.trim();
            if (limpio.isEmpty()) continue;
            long id;
            try {
                id = Long.parseLong(limpio);
            } catch (NumberFormatException ex) {
                throw new NegocioException("La categoría elegida no existe");
            }
            categorias.findByIdAndComercioId(id, sesion.comercioId())
                    .orElseThrow(() -> new NegocioException("La categoría elegida no pertenece a este comercio"));
            if (!ids.contains(limpio)) ids.add(limpio);
            if (ids.size() > 12) throw new NegocioException("Podés destacar hasta 12 categorías");
        }
        return String.join(",", ids);
    }

    private java.util.List<Map<String, Object>> rubrosVisibles(Vidriera vidriera) {
        java.util.List<Map<String, Object>> salida = new java.util.ArrayList<>();
        String texto = vidriera.getRubros() == null ? "" : vidriera.getRubros();
        for (String parte : texto.split(",")) {
            if (parte.isBlank()) continue;
            categorias.findByIdAndComercioId(Long.parseLong(parte.trim()), vidriera.getComercio().getId()).ifPresent(categoria -> {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", categoria.getId());
                item.put("nombre", categoria.getNombre());
                salida.add(item);
            });
        }
        return salida;
    }

    private String seccionesValidas(String crudo) {
        java.util.List<java.util.Map<String, Object>> entrada = parsearSecciones(crudo);
        if (entrada.size() > 6) throw new NegocioException("Podés crear hasta 6 secciones");
        java.util.List<java.util.Map<String, Object>> limpias = new java.util.ArrayList<>();
        int n = 0;
        for (java.util.Map<String, Object> item : entrada) {
            String tipo = String.valueOf(item.getOrDefault("tipo", ""));
            if (!tipo.equals("texto") && !tipo.equals("productos")) throw new NegocioException("Hay una sección que no existe");
            java.util.Map<String, Object> limpio = new java.util.LinkedHashMap<>();
            limpio.put("id", idSeccion(item.get("id"), n++));
            limpio.put("tipo", tipo);
            limpio.put("titulo", texto(item.get("titulo") == null ? "" : String.valueOf(item.get("titulo")), 80, "El título de la sección"));
            if (tipo.equals("texto")) {
                limpio.put("texto", texto(item.get("texto") == null ? "" : String.valueOf(item.get("texto")), 400, "El texto de la sección"));
            } else {
                long id;
                try {
                    id = Long.parseLong(String.valueOf(item.get("categoriaId")));
                } catch (NumberFormatException ex) {
                    throw new NegocioException("Elegí una categoría para la sección");
                }
                categorias.findByIdAndComercioId(id, sesion.comercioId())
                        .orElseThrow(() -> new NegocioException("La categoría de la sección no pertenece a este comercio"));
                limpio.put("categoriaId", id);
            }
            limpias.add(limpio);
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(limpias);
        } catch (java.io.IOException ex) {
            throw new NegocioException("No se pudieron guardar las secciones");
        }
    }

    private java.util.List<java.util.Map<String, Object>> seccionesVisibles(Vidriera vidriera) {
        java.util.List<java.util.Map<String, Object>> salida = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> item : parsearSecciones(vidriera.getSecciones())) {
            java.util.Map<String, Object> visible = new java.util.LinkedHashMap<>();
            visible.put("id", item.get("id"));
            visible.put("tipo", item.get("tipo"));
            visible.put("titulo", item.get("titulo") == null ? "" : item.get("titulo"));
            if ("texto".equals(item.get("tipo"))) {
                visible.put("texto", item.get("texto") == null ? "" : item.get("texto"));
                salida.add(visible);
                continue;
            }
            Object crudo = item.get("categoriaId");
            if (crudo == null) continue;
            categorias.findByIdAndComercioId(Long.parseLong(String.valueOf(crudo)), vidriera.getComercio().getId()).ifPresent(categoria -> {
                visible.put("categoriaId", categoria.getId());
                visible.put("categoria", categoria.getNombre());
                salida.add(visible);
            });
        }
        return salida;
    }

    private java.util.List<java.util.Map<String, Object>> parsearSecciones(String crudo) {
        if (crudo == null || crudo.isBlank()) return java.util.List.of();
        try {
            Object leido = new com.fasterxml.jackson.databind.ObjectMapper().readValue(crudo, java.util.List.class);
            if (!(leido instanceof java.util.List<?> lista)) return java.util.List.of();
            java.util.List<java.util.Map<String, Object>> salida = new java.util.ArrayList<>();
            for (Object item : lista) {
                if (item instanceof java.util.Map<?, ?> mapa) {
                    java.util.Map<String, Object> copia = new java.util.LinkedHashMap<>();
                    mapa.forEach((clave, valor) -> copia.put(String.valueOf(clave), valor));
                    salida.add(copia);
                }
            }
            return salida;
        } catch (java.io.IOException ex) {
            throw new NegocioException("Las secciones no tienen un formato válido");
        }
    }

    private String idSeccion(Object valor, int indice) {
        String id = valor == null ? "" : String.valueOf(valor).trim();
        if (id.matches("[A-Za-z0-9_-]{1,40}")) return id;
        return "s" + indice;
    }

    private String publicoDe(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim().toLowerCase();
        if (!java.util.List.of("hombre", "mujer").contains(limpio)) {
            throw new NegocioException("Elegí hombre, mujer o dejalo sin marcar");
        }
        return limpio;
    }

    private static String opcion(String valor, java.util.List<String> validas, String mensaje) {
        if (valor == null || !validas.contains(valor)) throw new NegocioException(mensaje);
        return valor;
    }

    private static String colorHex(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (!limpio.matches("#[0-9A-Fa-f]{6}")) {
            throw new NegocioException("El color tiene que ser un hexadecimal, por ejemplo #7C3AED");
        }
        return limpio.toLowerCase();
    }

    private static final java.util.List<String> TEMAS = java.util.List.of("giro", "oscuro", "esmeralda", "terracota", "pastel", "neon");
    private static final java.util.List<String> FONDOS = java.util.List.of("blanco", "gris", "calido", "oscuro");
    private static final java.util.List<String> TEXTOS = java.util.List.of("oscuro", "claro");
    private static final java.util.List<String> CATALOGOS = java.util.List.of("grilla", "lista");
    private static final java.util.List<String> HEROES = java.util.List.of("gradiente", "foto", "tipografico", "ninguno");
    private static final java.util.List<String> FUENTES = java.util.List.of("jakarta", "inter", "playfair", "grotesk");
    private static final java.util.List<String> MENUS = java.util.List.of("centro", "lados", "esquinas");
    private static final java.util.List<String> FORMAS = java.util.List.of("cuadrado", "curvo", "circular");
    private static final java.util.List<String> FORMAS_RUBRO = java.util.List.of("recto", "redondeado", "circular", "pastilla");
    private static final java.util.List<String> FORMAS_SUMAR = java.util.List.of("circular", "pastilla", "cuadrado");
}
