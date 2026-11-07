package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.Caja;
import com.giro.domain.Cliente;
import com.giro.domain.DetallePedido;
import com.giro.domain.DetalleVenta;
import com.giro.domain.EstadoCaja;
import com.giro.domain.EstadoPedido;
import com.giro.domain.EstadoVenta;
import com.giro.domain.MedioPago;
import com.giro.domain.MovimientoCaja;
import com.giro.domain.MovimientoStock;
import com.giro.domain.Pago;
import com.giro.domain.Pedido;
import com.giro.domain.Producto;
import com.giro.domain.TipoMovimientoCaja;
import com.giro.domain.TipoMovimientoStock;
import com.giro.domain.RolComercio;
import com.giro.domain.Usuario;
import com.giro.domain.Variante;
import com.giro.domain.Venta;
import com.giro.error.NegocioException;
import com.giro.repo.CajaRepository;
import com.giro.repo.MovimientoCajaRepository;
import com.giro.repo.MovimientoStockRepository;
import com.giro.repo.PagoRepository;
import com.giro.repo.PedidoRepository;
import com.giro.repo.ProductoRepository;
import com.giro.repo.VarianteRepository;
import com.giro.repo.VentaRepository;
import com.giro.repo.UsuarioRepository;
import com.giro.seguridad.Sesion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class ComercialService {
    private final Sesion sesion;
    private final CatalogoService catalogo;
    private final ProductoRepository productos;
    private final VarianteRepository variantes;
    private final VentaRepository ventas;
    private final PagoRepository pagos;
    private final MovimientoStockRepository stock;
    private final MovimientoCajaRepository movimientos;
    private final CajaRepository cajas;
    private final PedidoRepository pedidos;
    private final EnvioService envios;
    private final UsuarioRepository usuarios;

    public ComercialService(Sesion sesion, CatalogoService catalogo, ProductoRepository productos,
                            VarianteRepository variantes, VentaRepository ventas, PagoRepository pagos,
                            MovimientoStockRepository stock, MovimientoCajaRepository movimientos,
                            CajaRepository cajas, PedidoRepository pedidos, EnvioService envios,
                            UsuarioRepository usuarios) {
        this.sesion = sesion;
        this.catalogo = catalogo;
        this.productos = productos;
        this.variantes = variantes;
        this.ventas = ventas;
        this.pagos = pagos;
        this.stock = stock;
        this.movimientos = movimientos;
        this.cajas = cajas;
        this.pedidos = pedidos;
        this.envios = envios;
        this.usuarios = usuarios;
    }

    @Transactional
    public Map<String, Object> vender(Modelos.VentaEntrada entrada) {
        List<Modelos.Item> items = entrada.items();
        return registrar(entrada.clienteId(), entrada.medio(), items, null, true);
    }

    @Transactional
    public Map<String, Object> crearPedido(Modelos.PedidoEntrada entrada) {
        sesion.exigirSuscripcion();
        Pedido pedido = new Pedido();
        pedido.setComercio(sesion.usuario().getComercio());
        pedido.setEstado(EstadoPedido.PENDIENTE);
        pedido.setCreadoEn(LocalDateTime.now(Sesion.ZONA));
        if (entrada.clienteId() != null) {
            pedido.setCliente(catalogo.clientePropio(entrada.clienteId()));
        }
        cargarEntrega(pedido, entrada);
        BigDecimal total = BigDecimal.ZERO;
        for (Modelos.Item item : entrada.items()) {
            Producto producto = productos.lock(item.productoId(), sesion.comercioId())
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            if (!producto.isActivo()) {
                throw new NegocioException("El producto está desactivado");
            }
            Variante variante = resolverVariante(producto, item.varianteId());
            BigDecimal cantidad = Textos.cantidad(item.cantidad());
            BigDecimal precio = catalogo.precioVigente(producto, variante);
            BigDecimal subtotal = precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setVariante(variante);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(subtotal);
            pedido.getDetalles().add(detalle);
            total = total.add(subtotal);
        }
        envios.aplicar(pedido);
        pedido.setTotal(total.add(pedido.getEnvio()));
        pedidos.save(pedido);
        return vistaPedido(pedido);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarPedidos() {
        return pedidos.findByComercioIdOrderByCreadoEnDesc(sesion.comercioId()).stream().map(this::vistaPedido).toList();
    }

    @Transactional
    public Map<String, Object> cancelarPedido(Long id) {
        sesion.exigirSuscripcion();
        Pedido pedido = pedidoPropio(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new NegocioException("Solo se puede cancelar un pedido pendiente");
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        return vistaPedido(pedido);
    }

    @Transactional
    public Map<String, Object> confirmarPedido(Long id, Modelos.ConfirmarPedido entrada) {
        Pedido pedido = pedidoPropio(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new NegocioException("El pedido ya no está pendiente");
        }
        List<Modelos.Item> items = pedido.getDetalles().stream()
                .map(d -> new Modelos.Item(
                        d.getProducto().getId(),
                        d.getVariante() == null ? null : d.getVariante().getId(),
                        d.getCantidad()))
                .toList();
        Long clienteId = pedido.getCliente() == null ? null : pedido.getCliente().getId();
        Map<String, Object> venta = registrar(clienteId, entrada.medio(), items, pedido, false);
        pedido.setEstado(EstadoPedido.ENTREGADO);
        return venta;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarVentas() {
        return ventas.findTop50ByComercioIdOrderByCreadaEnDesc(sesion.comercioId()).stream().map(this::vistaVenta).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verVenta(Long id) {
        Venta venta = ventas.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        return vistaVenta(venta);
    }

    @Transactional
    public Map<String, Object> anular(Long id) {
        sesion.exigirCaja();
        sesion.exigirSuscripcion();
        Venta venta = ventas.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("La venta ya está anulada");
        }
        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = productos.lock(detalle.getProducto().getId(), sesion.comercioId()).orElseThrow();
            if (detalle.getVariante() != null) {
                Variante variante = variantes.lock(detalle.getVariante().getId(), producto.getId()).orElseThrow();
                variante.setStock(variante.getStock().add(detalle.getCantidad()));
                moverStock(producto, variante, venta, TipoMovimientoStock.ENTRADA, detalle.getCantidad(), "Anulación de venta");
            } else {
                producto.setStock(producto.getStock().add(detalle.getCantidad()));
                moverStock(producto, null, venta, TipoMovimientoStock.ENTRADA, detalle.getCantidad(), "Anulación de venta");
            }
        }
        Pago pago = pagos.findByVentaId(venta.getId()).orElseThrow();
        if (pago.getMedio() == MedioPago.CUENTA_CORRIENTE) {
            Cliente cliente = venta.getCliente();
            if (cliente.getSaldo().compareTo(venta.getTotal()) < 0) {
                throw new NegocioException("El saldo del cliente ya fue cobrado en parte. Registrá el ajuste en caja antes de anular.");
            }
            cliente.setSaldo(cliente.getSaldo().subtract(venta.getTotal()));
        } else {
            moverCaja(asegurarHoy(), venta, TipoMovimientoCaja.EGRESO, venta.getTotal(), "Anulación de venta #" + venta.getId());
        }
        venta.setEstado(EstadoVenta.ANULADA);
        venta.setAnuladaEn(LocalDateTime.now(Sesion.ZONA));
        return vistaVenta(venta);
    }

    @Transactional
    public Map<String, Object> abrirCaja(Modelos.AbrirCaja entrada) {
        throw new NegocioException("La caja abre sola a las 00:00 y cierra a las 23:59");
    }

    @Transactional
    public Map<String, Object> movimiento(Modelos.MovimientoCajaEntrada entrada) {
        sesion.exigirCaja();
        sesion.exigirSuscripcion();
        Caja caja = asegurarHoy();
        moverCaja(caja, null, entrada.tipo(), Textos.dinero(entrada.monto(), "El monto"), entrada.concepto().trim());
        return vistaCaja(caja);
    }

    @Transactional
    public Map<String, Object> cerrarCaja(Modelos.CerrarCaja entrada) {
        throw new NegocioException("La caja abre sola a las 00:00 y cierra a las 23:59");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> cierres() {
        return cajas.findTop90ByComercioIdAndEstadoOrderByCerradaEnDesc(sesion.comercioId(), EstadoCaja.CERRADA).stream()
                .map(caja -> {
                    Map<String, Object> item = vistaCaja(caja);
                    BigDecimal contado = caja.getMontoContado();
                    BigDecimal teorico = (BigDecimal) item.get("esperado");
                    item.put("cerradaEn", caja.getCerradaEn() == null ? null : caja.getCerradaEn().toString());
                    item.put("diferencia", contado == null || teorico == null ? null : contado.subtract(teorico));
                    return item;
                }).toList();
    }

    @Transactional
    public Map<String, Object> cajaActual() {
        sincronizarComercio(sesion.usuario().getComercio());
        return cajas.findByComercioIdAndEstado(sesion.comercioId(), EstadoCaja.ABIERTA)
                .map(this::vistaCaja)
                .orElseGet(() -> Map.of("abierta", false));
    }

    @Transactional
    public Map<String, Object> cobrarSaldo(Long clienteId, Modelos.Cobro entrada) {
        sesion.exigirCaja();
        sesion.exigirSuscripcion();
        if (entrada.medio() == MedioPago.CUENTA_CORRIENTE) {
            throw new NegocioException("Un cobro de saldo no puede quedar otra vez en cuenta corriente");
        }
        Cliente cliente = catalogo.clientePropio(clienteId);
        BigDecimal monto = Textos.dinero(entrada.monto(), "El monto");
        if (cliente.getSaldo().compareTo(monto) < 0) {
            throw new NegocioException("El cobro supera el saldo del cliente");
        }
        cliente.setSaldo(cliente.getSaldo().subtract(monto));
        moverCaja(asegurarHoy(), null, cliente, TipoMovimientoCaja.INGRESO, monto, "Cobro de saldo de " + cliente.getNombre());
        return catalogo.fichaCliente(clienteId);
    }

    private Map<String, Object> registrar(Long clienteId, MedioPago medio, List<Modelos.Item> items, Pedido pedido, boolean precioActual) {
        sesion.exigirSuscripcion();
        if (items == null || items.isEmpty()) {
            throw new NegocioException("La venta no tiene productos");
        }
        Cliente cliente = clienteId == null ? null : catalogo.clientePropio(clienteId);
        if (medio == MedioPago.CUENTA_CORRIENTE && cliente == null) {
            throw new NegocioException("La cuenta corriente necesita un cliente");
        }
        Caja caja = asegurarHoy();
        Venta venta = new Venta();
        venta.setComercio(sesion.usuario().getComercio());
        venta.setCliente(cliente);
        venta.setUsuario(sesion.usuario());
        venta.setCaja(caja);
        venta.setPedido(pedido);
        venta.setEstado(EstadoVenta.CONFIRMADA);
        venta.setCreadaEn(LocalDateTime.now(Sesion.ZONA));

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal costo = BigDecimal.ZERO;
        List<MovimientoStock> pendientes = new ArrayList<>();
        int indice = 0;
        for (Modelos.Item item : items) {
            Producto producto = productos.lock(item.productoId(), sesion.comercioId())
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            if (!producto.isActivo()) {
                throw new NegocioException("El producto está desactivado");
            }
            Variante variante = resolverVariante(producto, item.varianteId());
            BigDecimal cantidad = Textos.cantidad(item.cantidad());
            BigDecimal precio = precioActual || pedido == null
                    ? catalogo.precioVigente(producto, variante)
                    : pedido.getDetalles().get(indice).getPrecioUnitario();
            descontar(producto, variante, cantidad);
            BigDecimal subtotal = precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(producto);
            detalle.setVariante(variante);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(precio);
            detalle.setCostoUnitario(producto.getCosto());
            detalle.setSubtotal(subtotal);
            venta.getDetalles().add(detalle);
            total = total.add(subtotal);
            costo = costo.add(producto.getCosto().multiply(cantidad));
            MovimientoStock movimiento = new MovimientoStock();
            movimiento.setComercio(producto.getComercio());
            movimiento.setProducto(producto);
            movimiento.setVariante(variante);
            movimiento.setTipo(TipoMovimientoStock.SALIDA);
            movimiento.setCantidad(cantidad);
            movimiento.setMotivo("Venta");
            pendientes.add(movimiento);
            indice++;
        }
        if (pedido != null && pedido.getEnvio() != null) {
            total = total.add(pedido.getEnvio());
        }
        venta.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        venta.setCostoTotal(costo.setScale(2, RoundingMode.HALF_UP));
        ventas.saveAndFlush(venta);
        Pago pago = new Pago();
        pago.setVenta(venta);
        pago.setMedio(medio);
        pago.setMonto(venta.getTotal());
        pagos.save(pago);
        for (MovimientoStock movimiento : pendientes) {
            movimiento.setVenta(venta);
            stock.save(movimiento);
        }
        if (medio == MedioPago.CUENTA_CORRIENTE) {
            if (!cliente.isCuentaCorriente()) {
                throw new NegocioException("Este cliente no tiene cuenta corriente habilitada");
            }
            if (cliente.getLimiteCredito() != null
                    && cliente.getSaldo().add(venta.getTotal()).compareTo(cliente.getLimiteCredito()) > 0) {
                throw new NegocioException("La venta supera el límite de crédito");
            }
            cliente.setSaldo(cliente.getSaldo().add(venta.getTotal()));
        } else {
            moverCaja(caja, venta, TipoMovimientoCaja.INGRESO, venta.getTotal(), "Venta #" + venta.getId());
        }
        return vistaVenta(venta);
    }

    private Variante resolverVariante(Producto producto, Long varianteId) {
        if (producto.isUsaVariantes()) {
            if (varianteId == null) {
                throw new NegocioException("Elegí una variante de " + producto.getNombre());
            }
            Variante variante = variantes.lock(varianteId, producto.getId())
                    .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "La variante no pertenece al producto"));
            if (!variante.isActivo()) {
                throw new NegocioException("La variante está desactivada");
            }
            return variante;
        }
        if (varianteId != null) {
            throw new NegocioException(producto.getNombre() + " no usa variantes");
        }
        return null;
    }

    private void descontar(Producto producto, Variante variante, BigDecimal cantidad) {
        if (variante != null) {
            if (variante.getStock().compareTo(cantidad) < 0) {
                throw new NegocioException("Stock insuficiente de " + producto.getNombre() + " " + variante.getNombre());
            }
            variante.setStock(variante.getStock().subtract(cantidad));
            return;
        }
        if (producto.getStock().compareTo(cantidad) < 0) {
            throw new NegocioException("Stock insuficiente de " + producto.getNombre());
        }
        producto.setStock(producto.getStock().subtract(cantidad));
    }

    private void moverStock(Producto producto, Variante variante, Venta venta, TipoMovimientoStock tipo, BigDecimal cantidad, String motivo) {
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setComercio(producto.getComercio());
        movimiento.setProducto(producto);
        movimiento.setVariante(variante);
        movimiento.setVenta(venta);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setMotivo(motivo);
        stock.save(movimiento);
    }

    private void moverCaja(Caja caja, Venta venta, TipoMovimientoCaja tipo, BigDecimal monto, String concepto) {
        moverCaja(caja, venta, venta == null ? null : venta.getCliente(), tipo, monto, concepto);
    }

    private void moverCaja(Caja caja, Venta venta, Cliente cliente, TipoMovimientoCaja tipo, BigDecimal monto, String concepto) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setComercio(caja.getComercio());
        movimiento.setCaja(caja);
        movimiento.setVenta(venta);
        movimiento.setCliente(cliente);
        movimiento.setTipo(tipo);
        movimiento.setMonto(monto);
        movimiento.setConcepto(concepto);
        movimiento.setCreadoEn(LocalDateTime.now(Sesion.ZONA));
        movimientos.save(movimiento);
    }

    @Transactional
    public void sincronizarComercio(com.giro.domain.Comercio comercio) {
        LocalDate hoy = LocalDate.now(Sesion.ZONA);
        java.util.Optional<Caja> abierta = cajas.findByComercioIdAndEstado(comercio.getId(), EstadoCaja.ABIERTA);
        if (abierta.isPresent() && !abierta.get().getAbiertaEn().toLocalDate().isBefore(hoy)) return;
        BigDecimal fondo = BigDecimal.ZERO;
        LocalDate desde = hoy;
        if (abierta.isPresent()) {
            Caja vieja = abierta.get();
            cerrarAlCierreDelDia(vieja);
            fondo = vieja.getMontoContado();
            desde = vieja.getAbiertaEn().toLocalDate().plusDays(1);
        } else {
            java.util.Optional<Caja> ultima = cajas.findFirstByComercioIdOrderByAbiertaEnDesc(comercio.getId());
            if (ultima.isPresent()) {
                Caja previa = ultima.get();
                fondo = previa.getMontoContado() != null ? previa.getMontoContado() : previa.getMontoInicial();
                desde = previa.getAbiertaEn().toLocalDate().plusDays(1);
            }
        }
        if (desde.isAfter(hoy)) {
            java.util.Optional<Caja> ultimaHoy = cajas.findFirstByComercioIdOrderByAbiertaEnDesc(comercio.getId());
            if (ultimaHoy.isPresent() && ultimaHoy.get().getEstado() == EstadoCaja.CERRADA && ultimaHoy.get().getAbiertaEn().toLocalDate().equals(hoy)) {
                Caja caja = ultimaHoy.get();
                caja.setEstado(EstadoCaja.ABIERTA);
                caja.setCerradaEn(null);
                caja.setMontoContado(null);
            }
            return;
        }
        Usuario quien = responsable(comercio);
        if (quien == null) return;
        for (LocalDate dia = desde; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            Caja caja = new Caja();
            caja.setComercio(comercio);
            caja.setAbiertaPor(quien);
            caja.setMontoInicial(fondo);
            caja.setAbiertaEn(dia.atStartOfDay());
            if (dia.isBefore(hoy)) {
                caja.setEstado(EstadoCaja.CERRADA);
                caja.setCerradaEn(dia.atTime(23, 59, 59));
                caja.setMontoContado(fondo);
            } else {
                caja.setEstado(EstadoCaja.ABIERTA);
            }
            cajas.save(caja);
        }
    }

    private void cerrarAlCierreDelDia(Caja caja) {
        caja.setMontoContado(efectivoTeorico(caja));
        caja.setEstado(EstadoCaja.CERRADA);
        caja.setCerradaEn(caja.getAbiertaEn().toLocalDate().atTime(23, 59, 59));
    }

    private Usuario responsable(com.giro.domain.Comercio comercio) {
        try {
            Usuario actual = sesion.usuario();
            if (actual.getComercio().getId().equals(comercio.getId())) return actual;
        } catch (NegocioException ex) {
            // El turno de las 00:00 no tiene una sesión.
        }
        java.util.List<Usuario> gente = usuarios.findByComercioIdOrderByNombreAsc(comercio.getId());
        return gente.stream().filter(usuario -> usuario.getRol() == RolComercio.DUENO).findFirst()
                .orElse(gente.isEmpty() ? null : gente.get(0));
    }

    private Caja asegurarHoy() {
        sincronizarComercio(sesion.usuario().getComercio());
        return cajas.findByComercioIdAndEstado(sesion.comercioId(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new NegocioException("No se pudo abrir la caja de hoy"));
    }

    private Pedido pedidoPropio(Long id) {
        return pedidos.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    private BigDecimal efectivoTeorico(Caja caja) {
        BigDecimal total = caja.getMontoInicial();
        for (MovimientoCaja movimiento : movimientos.findByCajaIdOrderByCreadoEnAsc(caja.getId())) {
            MedioPago medio = medioDe(movimiento);
            if (medio != null && medio != MedioPago.EFECTIVO) continue;
            if (movimiento.getTipo() == TipoMovimientoCaja.INGRESO) total = total.add(movimiento.getMonto());
            else total = total.subtract(movimiento.getMonto());
        }
        return total;
    }

    private MedioPago medioDe(MovimientoCaja movimiento) {
        if (movimiento.getVenta() == null) return null;
        return pagos.findByVentaId(movimiento.getVenta().getId()).map(Pago::getMedio).orElse(null);
    }

    private Map<String, Object> vistaCaja(Caja caja) {
        BigDecimal ingresos = movimientos.sumar(caja.getId(), TipoMovimientoCaja.INGRESO);
        BigDecimal egresos = movimientos.sumar(caja.getId(), TipoMovimientoCaja.EGRESO);
        BigDecimal efectivo = caja.getMontoInicial();
        BigDecimal ventasEfectivo = BigDecimal.ZERO;
        BigDecimal transferencia = BigDecimal.ZERO;
        BigDecimal tarjeta = BigDecimal.ZERO;
        BigDecimal manualIn = BigDecimal.ZERO;
        BigDecimal manualOut = BigDecimal.ZERO;
        int nEfectivo = 0;
        int nTransferencia = 0;
        int nTarjeta = 0;
        List<MovimientoCaja> lista = movimientos.findByCajaIdOrderByCreadoEnAsc(caja.getId());
        for (MovimientoCaja movimiento : lista) {
            MedioPago medio = medioDe(movimiento);
            boolean suma = movimiento.getTipo() == TipoMovimientoCaja.INGRESO;
            BigDecimal signo = suma ? movimiento.getMonto() : movimiento.getMonto().negate();
            if (movimiento.getVenta() == null) {
                if (suma) manualIn = manualIn.add(movimiento.getMonto());
                else manualOut = manualOut.add(movimiento.getMonto());
                efectivo = efectivo.add(signo);
                continue;
            }
            if (medio == MedioPago.TRANSFERENCIA) {
                transferencia = transferencia.add(signo);
                if (suma) nTransferencia++;
                else nTransferencia = Math.max(0, nTransferencia - 1);
            } else if (medio == MedioPago.TARJETA) {
                tarjeta = tarjeta.add(signo);
                if (suma) nTarjeta++;
                else nTarjeta = Math.max(0, nTarjeta - 1);
            } else if (medio == null || medio == MedioPago.EFECTIVO) {
                ventasEfectivo = ventasEfectivo.add(signo);
                efectivo = efectivo.add(signo);
                if (suma) nEfectivo++;
                else nEfectivo = Math.max(0, nEfectivo - 1);
            }
        }
        BigDecimal fiado = BigDecimal.ZERO;
        int nFiado = 0;
        List<Map<String, Object>> fiados = new ArrayList<>();
        for (Venta venta : ventas.findByComercioIdAndEstadoAndCreadaEnGreaterThanEqualOrderByCreadaEnAsc(sesion.comercioId(), EstadoVenta.CONFIRMADA, caja.getAbiertaEn())) {
            if (caja.getCerradaEn() != null && venta.getCreadaEn().isAfter(caja.getCerradaEn())) continue;
            Pago pago = pagos.findByVentaId(venta.getId()).orElse(null);
            if (pago == null || pago.getMedio() != MedioPago.CUENTA_CORRIENTE) continue;
            fiado = fiado.add(venta.getTotal());
            nFiado++;
            if (fiados.size() < 8) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("cliente", venta.getCliente() == null ? null : venta.getCliente().getNombre());
                item.put("total", venta.getTotal());
                fiados.add(item);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("abierta", caja.getEstado() == EstadoCaja.ABIERTA);
        body.put("id", caja.getId());
        body.put("montoInicial", caja.getMontoInicial());
        body.put("ingresos", ingresos);
        body.put("egresos", egresos);
        body.put("esperado", efectivo);
        body.put("ventasEfectivo", ventasEfectivo);
        body.put("ingresosManuales", manualIn);
        body.put("egresosManuales", manualOut);
        body.put("transferencia", transferencia);
        body.put("tarjeta", tarjeta);
        body.put("fiado", fiado);
        body.put("fiados", fiados);
        body.put("facturado", ventasEfectivo.add(transferencia).add(tarjeta).add(fiado));
        body.put("electronicos", transferencia.add(tarjeta));
        body.put("nEfectivo", nEfectivo);
        body.put("nTransferencia", nTransferencia);
        body.put("nTarjeta", nTarjeta);
        body.put("nFiado", nFiado);
        body.put("montoContado", caja.getMontoContado());
        body.put("estado", caja.getEstado().name());
        body.put("abiertaEn", caja.getAbiertaEn().toString());
        body.put("cajero", caja.getAbiertaPor().getNombre());
        body.put("movimientos", lista.stream().map(m -> {
            MedioPago medio = medioDe(m);
            Venta venta = m.getVenta();
            Cliente cliente = m.getCliente() != null ? m.getCliente() : (venta == null ? null : venta.getCliente());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tipo", m.getTipo().name());
            item.put("concepto", m.getConcepto());
            item.put("monto", m.getMonto());
            item.put("fecha", m.getCreadoEn().toString());
            item.put("venta", venta != null);
            item.put("ventaId", venta == null ? null : venta.getId());
            item.put("medio", medio == null ? null : medio.name());
            item.put("cliente", cliente == null ? null : cliente.getNombre());
            item.put("productos", productosDe(venta));
            return item;
        }).toList());
        return body;
    }

    private Map<String, Object> vistaVenta(Venta venta) {
        Pago pago = pagos.findByVentaId(venta.getId()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", venta.getId());
        body.put("estado", venta.getEstado().name());
        body.put("total", venta.getTotal());
        body.put("costo", venta.getCostoTotal());
        body.put("ganancia", venta.getTotal().subtract(venta.getCostoTotal()));
        body.put("medio", pago == null ? null : pago.getMedio().name());
        body.put("cliente", venta.getCliente() == null ? null : venta.getCliente().getNombre());
        body.put("clienteId", venta.getCliente() == null ? null : venta.getCliente().getId());
        body.put("fecha", venta.getCreadaEn().toString());
        body.put("items", venta.getDetalles().stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("producto", d.getProducto().getNombre());
            item.put("variante", d.getVariante() == null ? null : d.getVariante().getNombre());
            item.put("cantidad", d.getCantidad());
            item.put("precio", d.getPrecioUnitario());
            item.put("subtotal", d.getSubtotal());
            return item;
        }).toList());
        return body;
    }

    private Map<String, Object> vistaPedido(Pedido pedido) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", pedido.getId());
        body.put("estado", pedido.getEstado().name());
        body.put("total", pedido.getTotal());
        body.put("envio", pedido.getEnvio());
        body.put("distanciaKm", pedido.getDistanciaKm());
        body.put("puntoNombre", pedido.getPuntoNombre());
        body.put("cliente", pedido.getCliente() == null ? null : pedido.getCliente().getNombre());
        body.put("clienteId", pedido.getCliente() == null ? null : pedido.getCliente().getId());
        body.put("telefono", pedido.getCliente() == null ? null : pedido.getCliente().getTelefono());
        body.put("categoria", pedido.getCliente() == null ? null : pedido.getCliente().getCategoria());
        body.put("nota", pedido.getCliente() == null ? null : pedido.getCliente().getObservaciones());
        body.put("fecha", pedido.getCreadoEn().toString());
        body.put("entrega", pedido.getEntrega());
        body.put("direccion", pedido.getCalle());
        body.put("altura", pedido.getAltura());
        body.put("localidad", pedido.getLocalidad());
        body.put("partido", pedido.getPartido());
        body.put("provincia", pedido.getProvincia());
        body.put("codigoPostal", pedido.getCodigoPostal());
        body.put("items", pedido.getDetalles().stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            String sku = d.getVariante() != null && d.getVariante().getSku() != null ? d.getVariante().getSku() : d.getProducto().getSku();
            item.put("productoId", d.getProducto().getId());
            item.put("varianteId", d.getVariante() == null ? null : d.getVariante().getId());
            item.put("producto", d.getProducto().getNombre());
            item.put("variante", d.getVariante() == null ? null : d.getVariante().getNombre());
            item.put("sku", sku);
            item.put("cantidad", d.getCantidad());
            item.put("precio", d.getPrecioUnitario());
            item.put("subtotal", d.getSubtotal());
            return item;
        }).toList());
        return body;
    }

    private static final Set<String> ENTREGAS = Set.of("LOCAL", "DOMICILIO", "PUNTO");

    private void cargarEntrega(Pedido pedido, Modelos.PedidoEntrada entrada) {
        String modo = entrada.entrega() == null || entrada.entrega().isBlank()
                ? "LOCAL"
                : entrada.entrega().trim().toUpperCase(Locale.ROOT);
        if (!ENTREGAS.contains(modo)) {
            throw new NegocioException("Elegí retiro en el local, domicilio o punto de retiro");
        }
        pedido.setEntrega(modo);
        if ("LOCAL".equals(modo)) {
            pedido.setCalle(null);
            pedido.setAltura(null);
            pedido.setLocalidad(null);
            pedido.setPartido(null);
            pedido.setProvincia(null);
            pedido.setCodigoPostal(null);
            return;
        }
        pedido.setCalle(exigir(entrada.direccion(), 120, "la dirección"));
        pedido.setAltura(exigir(entrada.altura(), 16, "la altura"));
        pedido.setLocalidad(exigir(entrada.localidad(), 80, "la localidad"));
        pedido.setPartido(exigir(entrada.partido(), 80, "el partido"));
        pedido.setProvincia(provincia(entrada.provincia()));
        pedido.setCodigoPostal(codigoPostal(entrada.codigoPostal()));
    }

    private String exigir(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new NegocioException("Completá " + campo);
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new NegocioException("Revisá " + campo + ": tiene demasiados caracteres");
        }
        return limpio;
    }

    private String provincia(String valor) {
        String canon = Provincias.canon(valor);
        if (canon == null) throw new NegocioException("Completá la provincia");
        return canon;
    }

    private String codigoPostal(String valor) {
        String postal = Provincias.codigoPostal(valor);
        if (postal == null) throw new NegocioException("Completá el código postal");
        return postal;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> historial(Integer anio, Integer mes, Integer dia, Long clienteId, Long productoId, String medio, String tipo, String q) {
        return historial(anio, mes, dia, clienteId, productoId, medio, tipo, q, "dia");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> historial(Integer anio, Integer mes, Integer dia, Long clienteId, Long productoId, String medio, String tipo, String q, String alcance) {
        LocalDate hoy = LocalDate.now(Sesion.ZONA);
        int anioCursor = anio == null ? hoy.getYear() : anio;
        int mesCursor = mes == null ? hoy.getMonthValue() : mes;
        if (anioCursor < 2000 || anioCursor > 2100 || mesCursor < 1 || mesCursor > 12) {
            throw new NegocioException("La fecha del historial no es válida");
        }
        int diaCursor = Math.min(dia == null ? hoy.getDayOfMonth() : dia, YearMonth.of(anioCursor, mesCursor).lengthOfMonth());
        if (diaCursor < 1) throw new NegocioException("La fecha del historial no es válida");
        LocalDate cursor = LocalDate.of(anioCursor, mesCursor, diaCursor);
        String medioFiltro = medioPago(medio);
        String tipoFiltro = tipoFiltro(tipo);
        String texto = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Long comercioId = sesion.comercioId();

        Map<Long, Venta> ventasPorId = new LinkedHashMap<>();
        for (Venta venta : ventas.historial(comercioId)) ventasPorId.put(venta.getId(), venta);
        Map<Long, MedioPago> medios = new LinkedHashMap<>();
        for (Pago pago : pagos.delComercio(comercioId)) medios.put(pago.getVenta().getId(), pago.getMedio());
        Set<Long> ventasEnCaja = new java.util.HashSet<>();
        List<LineaCaja> lineas = new ArrayList<>();
        for (MovimientoCaja movimiento : movimientos.delComercio(comercioId)) {
            Venta venta = movimiento.getVenta() == null ? null : ventasPorId.get(movimiento.getVenta().getId());
            if (venta != null) ventasEnCaja.add(venta.getId());
            Cliente cliente = movimiento.getCliente() != null ? movimiento.getCliente() : (venta == null ? null : venta.getCliente());
            String clase = movimiento.getTipo() == TipoMovimientoCaja.EGRESO ? "EGRESO" : (venta == null ? "INGRESO" : "VENTA");
            MedioPago medioLinea = venta == null ? MedioPago.EFECTIVO : medios.get(venta.getId());
            lineas.add(new LineaCaja(movimiento.getCreadoEn(), clase, movimiento.getConcepto(), movimiento.getMonto(),
                    movimiento.getTipo() == TipoMovimientoCaja.INGRESO ? movimiento.getMonto() : BigDecimal.ZERO,
                    movimiento.getTipo() == TipoMovimientoCaja.EGRESO ? movimiento.getMonto() : BigDecimal.ZERO,
                    BigDecimal.ZERO, cliente, venta == null ? null : venta.getId(), medioLinea, null, productosDe(venta)));
        }
        for (Venta venta : ventasPorId.values()) {
            if (ventasEnCaja.contains(venta.getId())) continue;
            MedioPago medioLinea = medios.get(venta.getId());
            boolean anulada = venta.getEstado() == EstadoVenta.ANULADA;
            boolean fiado = !anulada && medioLinea == MedioPago.CUENTA_CORRIENTE;
            lineas.add(new LineaCaja(venta.getCreadaEn(), anulada ? "ANULADA" : "VENTA", "Venta #" + venta.getId(), venta.getTotal(),
                    anulada || fiado ? BigDecimal.ZERO : venta.getTotal(), BigDecimal.ZERO,
                    fiado ? venta.getTotal() : BigDecimal.ZERO, venta.getCliente(), venta.getId(), medioLinea,
                    venta.getEstado().name(), productosDe(venta)));
        }

        List<LineaCaja> visibles = lineas.stream().filter(linea -> linea.coincide(clienteId, productoId, medioFiltro, tipoFiltro, texto)).toList();
        Map<String, Acumulado> porDia = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Acumulado> porMes = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Acumulado> porAnio = new TreeMap<>(Comparator.reverseOrder());
        for (LineaCaja linea : visibles) {
            LocalDate fecha = linea.fecha.toLocalDate();
            porDia.computeIfAbsent(fecha.toString(), clave -> new Acumulado()).sumar(linea);
            porMes.computeIfAbsent(fecha.getYear() + "-" + String.format("%02d", fecha.getMonthValue()), clave -> new Acumulado()).sumar(linea);
            porAnio.computeIfAbsent(String.valueOf(fecha.getYear()), clave -> new Acumulado()).sumar(linea);
        }
        String claveDia = cursor.toString();
        String claveMes = cursor.getYear() + "-" + String.format("%02d", cursor.getMonthValue());
        String claveAnio = String.valueOf(cursor.getYear());
        String periodo = switch (alcance == null ? "" : alcance) {
            case "anio" -> "anio";
            case "mes" -> "mes";
            default -> "dia";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("anio", cursor.getYear());
        body.put("mes", cursor.getMonthValue());
        body.put("dia", cursor.getDayOfMonth());
        body.put("alcance", periodo);
        body.put("totales", Map.of(
                "dia", (porDia.getOrDefault(claveDia, new Acumulado())).vista(),
                "mes", (porMes.getOrDefault(claveMes, new Acumulado())).vista(),
                "anio", (porAnio.getOrDefault(claveAnio, new Acumulado())).vista()));
        body.put("anios", grupos(porAnio));
        body.put("meses", grupos(porMes).stream().filter(item -> String.valueOf(item.get("clave")).startsWith(claveAnio + "-")).toList());
        body.put("dias", grupos(porDia).stream().filter(item -> String.valueOf(item.get("clave")).startsWith(claveMes + "-")).toList());
        body.put("movimientos", visibles.stream()
                .filter(linea -> enAlcance(linea.fecha.toLocalDate(), cursor, periodo))
                .sorted(Comparator.comparing((LineaCaja linea) -> linea.fecha).reversed())
                .map(LineaCaja::vista)
                .toList());
        return body;
    }

    private boolean enAlcance(LocalDate fecha, LocalDate cursor, String periodo) {
        if ("anio".equals(periodo)) return fecha.getYear() == cursor.getYear();
        if ("mes".equals(periodo)) return fecha.getYear() == cursor.getYear() && fecha.getMonthValue() == cursor.getMonthValue();
        return fecha.equals(cursor);
    }

    @Transactional(readOnly = true)
    public byte[] arqueoPdf(Integer anio, Integer mes, Integer dia, Long clienteId, Long productoId, String medio, String tipo, String q, String alcance) {
        Map<String, Object> datos = historial(anio, mes, dia, clienteId, productoId, medio, tipo, q, alcance);
        String periodo = String.valueOf(datos.get("alcance"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> totales = (Map<String, Map<String, Object>>) datos.get("totales");
        Map<String, Object> total = totales.get(periodo);
        PdfTexto pdf = new PdfTexto();
        pdf.titulo("Arqueo de caja");
        pdf.linea(sesion.usuario().getComercio().getNombre());
        pdf.linea(tituloPeriodo(datos, periodo));
        pdf.linea(filtrosPdf(clienteId, productoId, medio, tipo, q));
        pdf.blanco();
        pdf.linea("Entró " + plata(total.get("entro")) + "    Salió " + plata(total.get("salio")) + "    Neto " + plata(total.get("neto")));
        if (numero(total.get("fiado")).signum() > 0) pdf.linea("A cuenta corriente " + plata(total.get("fiado")));
        pdf.linea(total.get("operaciones") + " movimientos");
        pdf.blanco();
        if ("anio".equals(periodo)) {
            pdf.subtitulo("Meses");
            for (Map<String, Object> grupo : lista(datos.get("meses"))) pdf.linea(filaGrupo(String.valueOf(grupo.get("clave")), grupo));
        } else if ("mes".equals(periodo)) {
            pdf.subtitulo("Días");
            for (Map<String, Object> grupo : lista(datos.get("dias"))) pdf.linea(filaGrupo(String.valueOf(grupo.get("clave")), grupo));
        }
        pdf.blanco();
        pdf.subtitulo("Movimientos");
        List<Map<String, Object>> movimientos = lista(datos.get("movimientos"));
        if (movimientos.isEmpty()) pdf.linea("Sin movimientos en este período.");
        for (Map<String, Object> movimiento : movimientos) pdf.linea(filaMovimiento(movimiento));
        return pdf.bytes();
    }

    private String tituloPeriodo(Map<String, Object> datos, String periodo) {
        int anio = ((Number) datos.get("anio")).intValue();
        int mes = ((Number) datos.get("mes")).intValue();
        int dia = ((Number) datos.get("dia")).intValue();
        String nombreMes = java.time.Month.of(mes).getDisplayName(java.time.format.TextStyle.FULL, Locale.forLanguageTag("es-AR"));
        if ("anio".equals(periodo)) return "Año " + anio;
        if ("mes".equals(periodo)) return nombreMes + " " + anio;
        return dia + " de " + nombreMes + " de " + anio;
    }

    private String filtrosPdf(Long clienteId, Long productoId, String medio, String tipo, String q) {
        List<String> partes = new ArrayList<>();
        if (clienteId != null) {
            Cliente cliente = catalogo.clientePropio(clienteId);
            partes.add("Cliente " + cliente.getNombre());
        }
        if (productoId != null) {
            Producto producto = productos.findByIdAndComercioId(productoId, sesion.comercioId()).orElse(null);
            partes.add(producto == null ? "Producto" : "Producto " + producto.getNombre());
        }
        if (medio != null && !medio.isBlank()) partes.add(medio.replace('_', ' ').toLowerCase(Locale.ROOT));
        if (tipo != null && !tipo.isBlank() && !"todos".equalsIgnoreCase(tipo)) partes.add(tipo);
        if (q != null && !q.isBlank()) partes.add("\"" + q.trim() + "\"");
        return partes.isEmpty() ? "Sin filtros" : String.join(" · ", partes);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Object valor) {
        return valor instanceof List<?> cruda ? (List<Map<String, Object>>) cruda : List.of();
    }

    private String filaGrupo(String clave, Map<String, Object> grupo) {
        return clave + "    entró " + plata(grupo.get("entro")) + "    salió " + plata(grupo.get("salio")) + "    neto " + plata(grupo.get("neto"));
    }

    private String filaMovimiento(Map<String, Object> movimiento) {
        String hora = String.valueOf(movimiento.get("fecha")).replace('T', ' ');
        if (hora.length() > 16) hora = hora.substring(0, 16);
        String cliente = movimiento.get("cliente") == null ? "" : " · " + movimiento.get("cliente");
        String prendas = lista(movimiento.get("productos")).stream().map(item -> {
            String nombre = String.valueOf(item.get("nombre"));
            Object talle = item.get("variante");
            String pieza = talle == null ? nombre : nombre + " · " + talle;
            return numero(item.get("cantidad")).stripTrailingZeros().toPlainString() + " × " + pieza;
        }).reduce((a, b) -> a + ", " + b).orElse("");
        boolean egreso = "EGRESO".equals(movimiento.get("tipo"));
        Object monto = numero(movimiento.get("fiado")).signum() > 0 ? movimiento.get("fiado") : movimiento.get("monto");
        String titulo = prendas.isBlank() ? String.valueOf(movimiento.get("concepto")) : prendas;
        return hora + "  " + titulo + cliente + "  " + (egreso ? "-" : "+") + plata(monto);
    }

    private String plata(Object valor) {
        java.text.DecimalFormat formato = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.forLanguageTag("es-AR")));
        return "$ " + formato.format(numero(valor));
    }

    private BigDecimal numero(Object valor) {
        if (valor instanceof BigDecimal decimal) return decimal;
        if (valor instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return BigDecimal.ZERO;
    }

    private List<Map<String, Object>> grupos(Map<String, Acumulado> origen) {
        List<Map<String, Object>> lista = new ArrayList<>();
        origen.forEach((clave, acumulado) -> {
            Map<String, Object> item = acumulado.vista();
            item.put("clave", clave);
            lista.add(item);
        });
        return lista;
    }

    private List<Map<String, Object>> productosDe(Venta venta) {
        if (venta == null) return List.of();
        List<Map<String, Object>> items = new ArrayList<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", detalle.getProducto().getId());
            item.put("nombre", detalle.getProducto().getNombre());
            item.put("variante", detalle.getVariante() == null ? null : detalle.getVariante().getNombre());
            item.put("cantidad", detalle.getCantidad());
            items.add(item);
        }
        return items;
    }

    private String medioPago(String medio) {
        if (medio == null || medio.isBlank()) return null;
        try {
            return MedioPago.valueOf(medio.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String tipoFiltro(String tipo) {
        if (tipo == null) return null;
        String limpio = tipo.trim().toLowerCase(Locale.ROOT);
        return switch (limpio) {
            case "ventas", "ingresos", "egresos" -> limpio;
            default -> null;
        };
    }

    private static final class LineaCaja {
        private final LocalDateTime fecha;
        private final String tipo;
        private final String concepto;
        private final BigDecimal monto;
        private final BigDecimal entro;
        private final BigDecimal salio;
        private final BigDecimal fiado;
        private final Long clienteId;
        private final String cliente;
        private final Long ventaId;
        private final String medio;
        private final String estado;
        private final List<Map<String, Object>> productos;

        private LineaCaja(LocalDateTime fecha, String tipo, String concepto, BigDecimal monto, BigDecimal entro, BigDecimal salio,
                          BigDecimal fiado, Cliente cliente, Long ventaId, MedioPago medio, String estado, List<Map<String, Object>> productos) {
            this.fecha = fecha;
            this.tipo = tipo;
            this.concepto = concepto;
            this.monto = monto;
            this.entro = entro;
            this.salio = salio;
            this.fiado = fiado;
            this.clienteId = cliente == null ? null : cliente.getId();
            this.cliente = cliente == null ? null : cliente.getNombre();
            this.ventaId = ventaId;
            this.medio = medio == null ? null : medio.name();
            this.estado = estado;
            this.productos = productos;
        }

        private boolean coincide(Long clienteId, Long productoId, String medio, String tipo, String texto) {
            if (clienteId != null && !clienteId.equals(this.clienteId)) return false;
            if (productoId != null && productos.stream().noneMatch(item -> productoId.equals(numero(item.get("id"))))) return false;
            if (medio != null && !medio.equals(this.medio)) return false;
            if ("ventas".equals(tipo) && !"VENTA".equals(this.tipo) && !"ANULADA".equals(this.tipo)) return false;
            if ("ingresos".equals(tipo) && !"INGRESO".equals(this.tipo)) return false;
            if ("egresos".equals(tipo) && !"EGRESO".equals(this.tipo)) return false;
            if (texto.isBlank()) return true;
            String variante = productos.stream().map(item -> item.get("nombre") + " " + item.get("variante")).reduce("", (a, b) -> a + " " + b);
            return (concepto + " " + (cliente == null ? "" : cliente) + " " + (this.medio == null ? "" : this.medio) + " " + variante)
                    .toLowerCase(Locale.ROOT).contains(texto);
        }

        private Map<String, Object> vista() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fecha", fecha.toString());
            item.put("tipo", tipo);
            item.put("concepto", concepto);
            item.put("monto", monto);
            item.put("fiado", fiado);
            item.put("cliente", cliente);
            item.put("clienteId", clienteId);
            item.put("ventaId", ventaId);
            item.put("medio", medio);
            item.put("estado", estado);
            item.put("productos", productos);
            return item;
        }

        private static Long numero(Object valor) {
            if (valor instanceof Long largo) return largo;
            if (valor instanceof Number numero) return numero.longValue();
            return null;
        }
    }

    private static final class Acumulado {
        private BigDecimal entro = BigDecimal.ZERO;
        private BigDecimal salio = BigDecimal.ZERO;
        private BigDecimal fiado = BigDecimal.ZERO;
        private int operaciones;

        private void sumar(LineaCaja linea) {
            entro = entro.add(linea.entro);
            salio = salio.add(linea.salio);
            fiado = fiado.add(linea.fiado);
            operaciones++;
        }

        private Map<String, Object> vista() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entro", entro);
            item.put("salio", salio);
            item.put("neto", entro.subtract(salio));
            item.put("fiado", fiado);
            item.put("operaciones", operaciones);
            return item;
        }
    }
}
