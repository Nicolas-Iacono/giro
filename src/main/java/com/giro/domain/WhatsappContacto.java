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

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_contactos")
public class WhatsappContacto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @Column(nullable = false, length = 32)
    private String telefono;

    @Column(length = 160)
    private String nombre;

    @Column(name = "ultimo_texto", length = 500)
    private String ultimoTexto;

    @Column(name = "ultimo_en")
    private LocalDateTime ultimoEn;

    @Column(name = "no_leidos", nullable = false)
    private int noLeidos;

    @Column(name = "bot_activo", nullable = false)
    private boolean botActivo = true;

    @Column(name = "ultimo_cliente_en")
    private LocalDateTime ultimoClienteEn;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getUltimoTexto() { return ultimoTexto; }
    public void setUltimoTexto(String ultimoTexto) { this.ultimoTexto = ultimoTexto; }
    public LocalDateTime getUltimoEn() { return ultimoEn; }
    public void setUltimoEn(LocalDateTime ultimoEn) { this.ultimoEn = ultimoEn; }
    public int getNoLeidos() { return noLeidos; }
    public void setNoLeidos(int noLeidos) { this.noLeidos = noLeidos; }
    public boolean isBotActivo() { return botActivo; }
    public void setBotActivo(boolean botActivo) { this.botActivo = botActivo; }
    public LocalDateTime getUltimoClienteEn() { return ultimoClienteEn; }
    public void setUltimoClienteEn(LocalDateTime ultimoClienteEn) { this.ultimoClienteEn = ultimoClienteEn; }
}
