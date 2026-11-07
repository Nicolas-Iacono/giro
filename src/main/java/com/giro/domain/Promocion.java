package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promociones")
public class Promocion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precioPromo;

    private LocalDate desde;
    private LocalDate hasta;

    @Column(nullable = false)
    private boolean activa = true;

    public Long getId() { return id; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public BigDecimal getPrecioPromo() { return precioPromo; }
    public void setPrecioPromo(BigDecimal precioPromo) { this.precioPromo = precioPromo; }
    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }
    public LocalDate getHasta() { return hasta; }
    public void setHasta(LocalDate hasta) { this.hasta = hasta; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}
