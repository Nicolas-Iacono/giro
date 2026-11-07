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
@Table(name = "cajas")
public class Caja {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abierta_por", nullable = false)
    private Usuario abiertaPor;

    @Column(nullable = false)
    private LocalDateTime abiertaEn = LocalDateTime.now();

    private LocalDateTime cerradaEn;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal montoInicial;

    @Column(precision = 14, scale = 2)
    private BigDecimal montoContado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaja estado;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public void setAbiertaPor(Usuario abiertaPor) { this.abiertaPor = abiertaPor; }
    public Usuario getAbiertaPor() { return abiertaPor; }
    public LocalDateTime getAbiertaEn() { return abiertaEn; }
    public void setAbiertaEn(LocalDateTime abiertaEn) { this.abiertaEn = abiertaEn; }
    public LocalDateTime getCerradaEn() { return cerradaEn; }
    public void setCerradaEn(LocalDateTime cerradaEn) { this.cerradaEn = cerradaEn; }
    public BigDecimal getMontoInicial() { return montoInicial; }
    public void setMontoInicial(BigDecimal montoInicial) { this.montoInicial = montoInicial; }
    public BigDecimal getMontoContado() { return montoContado; }
    public void setMontoContado(BigDecimal montoContado) { this.montoContado = montoContado; }
    public EstadoCaja getEstado() { return estado; }
    public void setEstado(EstadoCaja estado) { this.estado = estado; }
}
