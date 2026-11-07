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

@Entity
@Table(name = "pagos")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MedioPago medio;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    public Venta getVenta() { return venta; }
    public void setVenta(Venta venta) { this.venta = venta; }
    public MedioPago getMedio() { return medio; }
    public void setMedio(MedioPago medio) { this.medio = medio; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
}
