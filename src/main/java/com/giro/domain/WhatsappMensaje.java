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
@Table(name = "whatsapp_mensajes")
public class WhatsappMensaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contacto_id", nullable = false)
    private WhatsappContacto contacto;

    @Column(nullable = false, length = 12)
    private String direccion;

    @Column(nullable = false, length = 12)
    private String origen;

    @Column(nullable = false, length = 4000)
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "meta_id", length = 128)
    private String metaId;

    @Column(length = 16)
    private String estado;

    public Long getId() { return id; }
    public WhatsappContacto getContacto() { return contacto; }
    public void setContacto(WhatsappContacto contacto) { this.contacto = contacto; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public String getMetaId() { return metaId; }
    public void setMetaId(String metaId) { this.metaId = metaId; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
