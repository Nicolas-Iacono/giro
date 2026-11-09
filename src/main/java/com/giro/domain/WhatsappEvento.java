package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_eventos")
public class WhatsappEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String dedupe;

    @Column(nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Column(nullable = false, length = 16)
    private String estado;

    @Column(nullable = false)
    private int intentos;

    @Column(length = 80)
    private String error;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "procesado_en")
    private LocalDateTime procesadoEn;

    public Long getId() { return id; }
    public String getDedupe() { return dedupe; }
    public void setDedupe(String dedupe) { this.dedupe = dedupe; }
    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getProcesadoEn() { return procesadoEn; }
    public void setProcesadoEn(LocalDateTime procesadoEn) { this.procesadoEn = procesadoEn; }
}
