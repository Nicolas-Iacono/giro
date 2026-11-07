package com.giro.servicio;

import com.giro.domain.DetalleVenta;
import com.giro.domain.EstadoPedido;
import com.giro.domain.EstadoVenta;
import com.giro.domain.ImagenProducto;
import com.giro.domain.MedioPago;
import com.giro.domain.MovimientoCaja;
import com.giro.domain.Pago;
import com.giro.domain.Pedido;
import com.giro.domain.Producto;
import com.giro.domain.TipoMovimientoCaja;
import com.giro.domain.Venta;
import com.giro.domain.Variante;
import com.giro.error.NegocioException;
import com.giro.repo.ClienteRepository;
import com.giro.repo.ImagenProductoRepository;
import com.giro.repo.MovimientoCajaRepository;
import com.giro.repo.PagoRepository;
import com.giro.repo.PedidoRepository;
import com.giro.repo.ProductoRepository;
import com.giro.repo.VarianteRepository;
import com.giro.repo.VentaRepository;
import com.giro.seguridad.Sesion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TableroService {
    private final Sesion sesion;
    private final VentaRepository ventas;
    private final PagoRepository pagos;
    private final MovimientoCajaRepository movimientos;
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final VarianteRepository variantes;
    private final ClienteRepository clientes;
    private final ImagenProductoRepository imagenes;

    public TableroService(Sesion sesion, VentaRepository ventas, PagoRepository pagos, MovimientoCajaRepository movimientos,
                          PedidoRepository pedidos, ProductoRepository productos, VarianteRepository variantes,
                          ClienteRepository clientes, ImagenProductoRepository imagenes) {
        this.sesion = sesion;
        this.ventas = ventas;
        this.pagos = pagos;
        this.movimientos = movimientos;
        this.pedidos = pedidos;
        this.productos = productos;
        this.variantes = variantes;
        this.clientes = clientes;
        this.imagenes = imagenes;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        Long comercioId = sesion.comercioId();
        LocalDate hoy = LocalDate.now(Sesion.ZONA);
        LocalDateTime inicioDia = hoy.atStartOfDay();
        LocalDateTime finDia = hoy.plusDays(1).atStartOfDay();
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = hoy.plusMonths(1).withDayOfMonth(1).atStartOfDay();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ventasDelDia", ventas.sumarVentas(comercioId, EstadoVenta.CONFIRMADA, inicioDia, finDia));
        body.put("ventasDelMes", ventas.sumarVentas(comercioId, EstadoVenta.CONFIRMADA, inicioMes, finMes));
        body.put("cobrosDelMes", ventas.sumarCobros(comercioId, EstadoVenta.CONFIRMADA, MedioPago.CUENTA_CORRIENTE, inicioMes, finMes));
        body.put("gastosDelMes", movimientos.gastosManuales(comercioId, TipoMovimientoCaja.EGRESO, inicioMes, finMes));
        body.put("egresosDelMes", egresos(comercioId, inicioMes, finMes));
        body.put("gananciaBrutaDelMes", ventas.sumarGanancia(comercioId, EstadoVenta.CONFIRMADA, inicioMes, finMes));
        body.put("pedidosPendientes", pedidos.countByComercioIdAndEstado(comercioId, EstadoPedido.PENDIENTE));
        body.put("stockBajo", stockBajo(comercioId));
        body.put("saldosPendientes", clientes
                .findByComercioIdAndActivoTrueAndSaldoGreaterThanOrderByNombreAsc(comercioId, BigDecimal.ZERO)
                .stream().map(c -> Map.<String, Object>of("id", c.getId(), "nombre", c.getNombre(), "saldo", c.getSaldo()))
                .toList());
        body.put("evolucion", evolucion(comercioId, hoy));
        body.put("aclaracion", "Las ventas suman lo vendido, aunque esté fiado. Los cobros son plata ingresada. La ganancia bruta es precio menos costo de las ventas confirmadas, sin gastos.");
        return body;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analitica(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new NegocioException("Elegí desde y hasta");
        if (hasta.isBefore(desde)) throw new NegocioException("La fecha final es anterior a la inicial");
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > 366) throw new NegocioException("El período puede cubrir hasta un año");

        Long comercioId = sesion.comercioId();
        LocalDate anteriorHasta = desde.minusDays(1);
        LocalDate anteriorDesde = anteriorHasta.minusDays(dias - 1);
        LocalDateTime inicio = anteriorDesde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        LocalDateTime corte = desde.atStartOfDay();

        Map<Long, Venta> unicas = new LinkedHashMap<>();
        for (Venta venta : ventas.delPeriodo(comercioId, EstadoVenta.CONFIRMADA, inicio, fin)) {
            unicas.putIfAbsent(venta.getId(), venta);
        }
        List<Venta> actuales = new ArrayList<>();
        List<Venta> previas = new ArrayList<>();
        for (Venta venta : unicas.values()) {
            if (venta.getCreadaEn().isBefore(corte)) previas.add(venta);
            else actuales.add(venta);
        }

        Map<MedioPago, BigDecimal> montoMedio = new EnumMap<>(MedioPago.class);
        Map<MedioPago, Integer> cuentaMedio = new EnumMap<>(MedioPago.class);
        for (Pago pago : pagos.delPeriodo(comercioId, EstadoVenta.CONFIRMADA, corte, fin)) {
            montoMedio.merge(pago.getMedio(), pago.getMonto() == null ? BigDecimal.ZERO : pago.getMonto(), BigDecimal::add);
            cuentaMedio.merge(pago.getMedio(), 1, Integer::sum);
        }

        Totales ahora = totales(actuales);
        Totales antes = totales(previas);
        Map<String, Object> variacion = new LinkedHashMap<>();
        variacion.put("facturado", variacion(ahora.facturado, antes.facturado));
        variacion.put("ganancia", variacion(ahora.ganancia, antes.ganancia));
        variacion.put("ticketPromedio", variacion(ahora.ticketPromedio, antes.ticketPromedio));
        variacion.put("unidades", variacion(ahora.unidades, antes.unidades));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("desde", desde.toString());
        body.put("hasta", hasta.toString());
        body.put("dias", dias);
        body.put("facturado", ahora.facturado);
        body.put("ganancia", ahora.ganancia);
        body.put("margen", ahora.margen);
        body.put("tickets", ahora.tickets);
        body.put("ticketPromedio", ahora.ticketPromedio);
        body.put("unidades", ahora.unidades);
        body.put("promedioDiario", ahora.facturado.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP));
        body.put("recompra", recompra(actuales));
        body.put("variacion", variacion);
        body.put("anteriorFacturado", antes.facturado);
        body.put("evolucion", serie(desde, hasta, actuales));
        body.put("evolucionAnterior", serie(anteriorDesde, anteriorHasta, previas));
        body.put("diaFuerte", diaFuerte(actuales, ahora.facturado));
        body.put("horaFuerte", horaFuerte(actuales, ahora.facturado));
        body.put("canales", canales(actuales, ahora.facturado));
        body.put("medios", medios(montoMedio, cuentaMedio));
        body.put("productos", productosTop(actuales));
        body.put("variantes", variantesTop(actuales, ahora.unidades));
        body.put("gastos", movimientos.gastosManuales(comercioId, TipoMovimientoCaja.EGRESO, corte, fin));
        body.put("egresos", egresos(comercioId, corte, fin));
        body.put("aclaracion", "Suma las ventas confirmadas del período, aunque estén fiadas. La ganancia es el precio menos el costo. El período anterior tiene la misma cantidad de días, justo antes.");
        return body;
    }

    private List<Map<String, Object>> egresos(Long comercioId, LocalDateTime desde, LocalDateTime hasta) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (MovimientoCaja movimiento : movimientos.manuales(comercioId, TipoMovimientoCaja.EGRESO, desde, hasta)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("concepto", movimiento.getConcepto());
            item.put("monto", movimiento.getMonto());
            item.put("fecha", movimiento.getCreadoEn().toString());
            lista.add(item);
        }
        return lista;
    }

    private List<Map<String, Object>> stockBajo(Long comercioId) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Producto producto : productos.stockBajo(comercioId)) {
            lista.add(Map.of("nombre", producto.getNombre(), "stock", producto.getStock(), "minimo", producto.getStockMinimo()));
        }
        for (Variante variante : variantes.stockBajo(comercioId)) {
            lista.add(Map.of(
                    "nombre", variante.getProducto().getNombre() + " " + variante.getNombre(),
                    "stock", variante.getStock(),
                    "minimo", variante.getProducto().getStockMinimo()));
        }
        return lista;
    }

    private List<Map<String, Object>> evolucion(Long comercioId, LocalDate hoy) {
        Map<LocalDate, BigDecimal> dias = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            dias.put(hoy.minusDays(i), BigDecimal.ZERO);
        }
        List<Venta> recientes = ventas.findByComercioIdAndEstadoAndCreadaEnGreaterThanEqualOrderByCreadaEnAsc(
                comercioId, EstadoVenta.CONFIRMADA, hoy.minusDays(13).atStartOfDay());
        for (Venta venta : recientes) {
            LocalDate dia = venta.getCreadaEn().toLocalDate();
            if (dias.containsKey(dia)) {
                dias.put(dia, dias.get(dia).add(venta.getTotal()));
            }
        }
        return dias.entrySet().stream()
                .map(e -> Map.<String, Object>of("dia", e.getKey().toString(), "total", e.getValue()))
                .toList();
    }

    private static Totales totales(List<Venta> lista) {
        Totales item = new Totales();
        for (Venta venta : lista) {
            item.facturado = item.facturado.add(nulo(venta.getTotal()));
            item.costo = item.costo.add(nulo(venta.getCostoTotal()));
            item.unidades = item.unidades.add(unidadesDe(venta));
            item.tickets++;
        }
        item.ganancia = item.facturado.subtract(item.costo);
        item.ticketPromedio = item.tickets == 0 ? null : item.facturado.divide(BigDecimal.valueOf(item.tickets), 2, RoundingMode.HALF_UP);
        item.margen = item.facturado.signum() == 0 || item.costo.signum() == 0 ? null : porcentaje(item.ganancia, item.facturado);
        item.unidades = limpio(item.unidades);
        return item;
    }

    private static BigDecimal recompra(List<Venta> lista) {
        Map<Long, Integer> tickets = new HashMap<>();
        Map<Long, BigDecimal> unidades = new HashMap<>();
        for (Venta venta : lista) {
            if (venta.getCliente() == null) continue;
            Long id = venta.getCliente().getId();
            tickets.merge(id, 1, Integer::sum);
            unidades.merge(id, unidadesDe(venta), BigDecimal::add);
        }
        BigDecimal conCliente = BigDecimal.ZERO;
        BigDecimal repetidas = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entrada : tickets.entrySet()) {
            BigDecimal cantidad = unidades.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
            conCliente = conCliente.add(cantidad);
            if (entrada.getValue() >= 2) repetidas = repetidas.add(cantidad);
        }
        return porcentaje(repetidas, conCliente);
    }

    private static List<Map<String, Object>> serie(LocalDate desde, LocalDate hasta, List<Venta> lista) {
        Map<LocalDate, BigDecimal> dias = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) dias.put(dia, BigDecimal.ZERO);
        for (Venta venta : lista) {
            LocalDate dia = venta.getCreadaEn().toLocalDate();
            if (dias.containsKey(dia)) dias.put(dia, dias.get(dia).add(nulo(venta.getTotal())));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        dias.forEach((dia, total) -> out.add(Map.of("dia", dia.toString(), "total", total)));
        return out;
    }

    private static Map<String, Object> diaFuerte(List<Venta> lista, BigDecimal facturado) {
        if (facturado.signum() == 0) return null;
        BigDecimal[] montos = new BigDecimal[8];
        for (int i = 0; i < montos.length; i++) montos[i] = BigDecimal.ZERO;
        for (Venta venta : lista) montos[venta.getCreadaEn().getDayOfWeek().getValue()] = montos[venta.getCreadaEn().getDayOfWeek().getValue()].add(nulo(venta.getTotal()));
        int mejor = 0;
        for (int i = 1; i <= 7; i++) if (montos[i].compareTo(montos[mejor]) > 0) mejor = i;
        if (mejor == 0) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nombre", DIAS[mejor]);
        item.put("parte", porcentaje(montos[mejor], facturado));
        return item;
    }

    private static Map<String, Object> horaFuerte(List<Venta> lista, BigDecimal facturado) {
        if (facturado.signum() == 0) return null;
        BigDecimal[] montos = new BigDecimal[24];
        for (int i = 0; i < montos.length; i++) montos[i] = BigDecimal.ZERO;
        for (Venta venta : lista) montos[venta.getCreadaEn().getHour()] = montos[venta.getCreadaEn().getHour()].add(nulo(venta.getTotal()));
        int mejor = 0;
        for (int i = 1; i < 24; i++) if (montos[i].compareTo(montos[mejor]) > 0) mejor = i;
        if (montos[mejor].signum() == 0) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("desde", String.format("%02d:00", mejor));
        item.put("hasta", String.format("%02d:00", (mejor + 1) % 24));
        item.put("parte", porcentaje(montos[mejor], facturado));
        return item;
    }

    private static List<Map<String, Object>> canales(List<Venta> lista, BigDecimal facturado) {
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        Map<String, Integer> cuentas = new HashMap<>();
        for (Venta venta : lista) {
            String clave = canalDe(venta);
            montos.merge(clave, nulo(venta.getTotal()), BigDecimal::add);
            cuentas.merge(clave, 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        montos.forEach((clave, total) -> {
            if (total.signum() == 0) return;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("clave", clave);
            item.put("total", total);
            item.put("tickets", cuentas.getOrDefault(clave, 0));
            item.put("parte", porcentaje(total, facturado));
            out.add(item);
        });
        out.sort((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")));
        return out;
    }

    private static List<Map<String, Object>> medios(Map<MedioPago, BigDecimal> montos, Map<MedioPago, Integer> cuentas) {
        BigDecimal suma = montos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> out = new ArrayList<>();
        montos.entrySet().stream()
                .filter(entrada -> entrada.getValue().signum() > 0)
                .sorted(Map.Entry.<MedioPago, BigDecimal>comparingByValue().reversed())
                .forEach(entrada -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("clave", entrada.getKey().name());
                    item.put("total", entrada.getValue());
                    item.put("tickets", cuentas.getOrDefault(entrada.getKey(), 0));
                    item.put("parte", porcentaje(entrada.getValue(), suma));
                    out.add(item);
                });
        return out;
    }

    private List<Map<String, Object>> productosTop(List<Venta> lista) {
        Map<Long, Acumulado> mapa = new LinkedHashMap<>();
        for (Venta venta : lista) {
            for (DetalleVenta detalle : venta.getDetalles()) {
                if (detalle.getProducto() == null) continue;
                Producto producto = detalle.getProducto();
                Acumulado item = mapa.computeIfAbsent(producto.getId(), id -> new Acumulado(producto));
                BigDecimal cantidad = nulo(detalle.getCantidad());
                item.unidades = item.unidades.add(cantidad);
                item.facturado = item.facturado.add(nulo(detalle.getSubtotal()));
                item.costo = item.costo.add(nulo(detalle.getCostoUnitario()).multiply(cantidad));
                if ((item.sku == null || item.sku.isBlank()) && detalle.getVariante() != null && detalle.getVariante().getSku() != null) {
                    item.sku = detalle.getVariante().getSku();
                }
            }
        }
        List<Acumulado> top = mapa.values().stream()
                .sorted(Comparator.comparing((Acumulado item) -> item.facturado).reversed())
                .limit(4)
                .toList();
        Map<Long, Long> fotos = fotosDe(top.stream().map(item -> item.id).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Acumulado item : top) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", item.id);
            fila.put("nombre", item.nombre);
            fila.put("sku", item.sku == null ? "" : item.sku);
            fila.put("unidades", limpio(item.unidades));
            fila.put("facturado", item.facturado);
            fila.put("margen", item.facturado.signum() == 0 || item.costo.signum() == 0 ? null : porcentaje(item.facturado.subtract(item.costo), item.facturado));
            fila.put("imagen", fotos.get(item.id));
            out.add(fila);
        }
        return out;
    }

    private Map<Long, Long> fotosDe(Collection<Long> ids) {
        Map<Long, Long> fotos = new HashMap<>();
        if (ids.isEmpty()) return fotos;
        for (ImagenProducto imagen : imagenes.deProductos(ids)) {
            fotos.putIfAbsent(imagen.getProducto().getId(), imagen.getId());
        }
        return fotos;
    }

    private static List<Map<String, Object>> variantesTop(List<Venta> lista, BigDecimal unidades) {
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        for (Venta venta : lista) {
            for (DetalleVenta detalle : venta.getDetalles()) {
                if (detalle.getVariante() == null || detalle.getVariante().getNombre() == null) continue;
                String nombre = detalle.getVariante().getNombre().trim();
                if (nombre.isEmpty()) continue;
                montos.merge(nombre, nulo(detalle.getCantidad()), BigDecimal::add);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        montos.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(4)
                .forEach(entrada -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nombre", entrada.getKey());
                    item.put("unidades", limpio(entrada.getValue()));
                    item.put("parte", porcentaje(entrada.getValue(), unidades));
                    out.add(item);
                });
        return out;
    }

    private static BigDecimal unidadesDe(Venta venta) {
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta detalle : venta.getDetalles()) total = total.add(nulo(detalle.getCantidad()));
        return total;
    }

    private static String canalDe(Venta venta) {
        Pedido pedido = venta.getPedido();
        if (pedido == null || pedido.getEntrega() == null || pedido.getEntrega().isBlank()) return "LOCAL";
        String entrega = pedido.getEntrega();
        if ("DOMICILIO".equals(entrega) || "PUNTO".equals(entrega) || "LOCAL".equals(entrega)) return entrega;
        return "LOCAL";
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (parte == null || total == null || total.signum() == 0) return null;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (actual == null || anterior == null || anterior.signum() == 0) return null;
        return actual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static BigDecimal limpio(BigDecimal valor) {
        BigDecimal texto = valor.stripTrailingZeros();
        return texto.scale() < 0 ? texto.setScale(0, RoundingMode.UNNECESSARY) : texto;
    }

    private static final String[] DIAS = {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos"};

    private static final class Totales {
        private BigDecimal facturado = BigDecimal.ZERO;
        private BigDecimal costo = BigDecimal.ZERO;
        private BigDecimal ganancia = BigDecimal.ZERO;
        private BigDecimal unidades = BigDecimal.ZERO;
        private int tickets;
        private BigDecimal ticketPromedio;
        private BigDecimal margen;
    }

    private static final class Acumulado {
        private final long id;
        private final String nombre;
        private String sku;
        private BigDecimal unidades = BigDecimal.ZERO;
        private BigDecimal facturado = BigDecimal.ZERO;
        private BigDecimal costo = BigDecimal.ZERO;

        private Acumulado(Producto producto) {
            this.id = producto.getId();
            this.nombre = producto.getNombre();
            this.sku = producto.getSku();
        }
    }
}
