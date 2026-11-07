package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_stock")
public class MovimientoStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variante_id")
    private Variante variante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoStock tipo;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public void setVariante(Variante variante) { this.variante = variante; }
    public void setVenta(Venta venta) { this.venta = venta; }
    public void setTipo(TipoMovimientoStock tipo) { this.tipo = tipo; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
