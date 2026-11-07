package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "comercios")
public class Comercio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 120)
    private String calle;

    @Column(length = 16)
    private String altura;

    @Column(length = 80)
    private String localidad;

    @Column(length = 80)
    private String partido;

    @Column(length = 40)
    private String provincia;

    @Column(name = "codigo_postal", length = 8)
    private String codigoPostal;

    @Column(name = "envio_base", precision = 14, scale = 2)
    private java.math.BigDecimal envioBase;

    @Column(name = "envio_km", precision = 14, scale = 2)
    private java.math.BigDecimal envioKm;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getCalle() { return calle; }
    public void setCalle(String calle) { this.calle = calle; }
    public String getAltura() { return altura; }
    public void setAltura(String altura) { this.altura = altura; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public String getPartido() { return partido; }
    public void setPartido(String partido) { this.partido = partido; }
    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }
    public String getCodigoPostal() { return codigoPostal; }
    public void setCodigoPostal(String codigoPostal) { this.codigoPostal = codigoPostal; }
    public java.math.BigDecimal getEnvioBase() { return envioBase; }
    public void setEnvioBase(java.math.BigDecimal envioBase) { this.envioBase = envioBase; }
    public java.math.BigDecimal getEnvioKm() { return envioKm; }
    public void setEnvioKm(java.math.BigDecimal envioKm) { this.envioKm = envioKm; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
