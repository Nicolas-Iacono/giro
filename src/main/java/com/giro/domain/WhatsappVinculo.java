package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_vinculos")
public class WhatsappVinculo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comercio_id", nullable = false)
    private Long comercioId;

    @Column(nullable = false, unique = true, length = 64)
    private String clave;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    @Column(nullable = false)
    private boolean usado;

    public Long getId() { return id; }
    public Long getComercioId() { return comercioId; }
    public void setComercioId(Long comercioId) { this.comercioId = comercioId; }
    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }
    public LocalDateTime getVenceEn() { return venceEn; }
    public void setVenceEn(LocalDateTime venceEn) { this.venceEn = venceEn; }
    public boolean isUsado() { return usado; }
    public void setUsado(boolean usado) { this.usado = usado; }
}
