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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "suscripciones")
public class Suscripcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false, unique = true)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado;

    @Column(nullable = false)
    private LocalDate venceEn;

    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public EstadoSuscripcion getEstado() { return estado; }
    public void setEstado(EstadoSuscripcion estado) { this.estado = estado; }
    public LocalDate getVenceEn() { return venceEn; }
    public void setVenceEn(LocalDate venceEn) { this.venceEn = venceEn; }
}
