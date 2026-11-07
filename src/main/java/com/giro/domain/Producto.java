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

@Entity
@Table(name = "productos")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false, length = 180)
    private String nombre;

    @Column(length = 80)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal stock = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean publicado;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private boolean usaVariantes;

    @Column(length = 20)
    private String publico;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getCosto() { return costo; }
    public void setCosto(BigDecimal costo) { this.costo = costo; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public BigDecimal getStock() { return stock; }
    public void setStock(BigDecimal stock) { this.stock = stock; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public boolean isPublicado() { return publicado; }
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public boolean isUsaVariantes() { return usaVariantes; }
    public void setUsaVariantes(boolean usaVariantes) { this.usaVariantes = usaVariantes; }
    public String getPublico() { return publico; }
    public void setPublico(String publico) { this.publico = publico; }
}
