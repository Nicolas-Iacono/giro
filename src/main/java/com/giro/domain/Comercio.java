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

    @Column(nullable = false, length = 20)
    private String canal = "FISICA";

    @Column(length = 800)
    private String rubro;

    @Column(nullable = false, length = 20)
    private String oferta = "PRODUCTOS";

    @Column(name = "detalles_producto", length = 200)
    private String detallesProducto;

    @Column(name = "mp_titular", length = 160)
    private String mpTitular;

    @Column(name = "mp_alias", length = 80)
    private String mpAlias;

    @Column(name = "mp_cvu", length = 22)
    private String mpCvu;

    @Column(name = "mp_cbu", length = 22)
    private String mpCbu;

    @Column(name = "wa_phone_number_id", length = 32)
    private String waPhoneNumberId;

    @Column(name = "wa_waba_id", length = 32)
    private String waWabaId;

    @Column(name = "wa_access_token", columnDefinition = "text")
    private String waAccessToken;

    @Column(name = "wa_numero", length = 40)
    private String waNumero;

    @Column(name = "wa_saludo", length = 500)
    private String waSaludo;

    @Column(name = "wa_activo", nullable = false)
    private boolean waActivo;

    @Column(name = "wa_nombre", length = 160)
    private String waNombre;

    @Column(name = "wa_tipo", length = 20)
    private String waTipo;

    @Column(name = "wa_estado", length = 20)
    private String waEstado;

    @Column(name = "wa_conectado_en")
    private LocalDateTime waConectadoEn;

    @Column(name = "wa_sincronizado_en")
    private LocalDateTime waSincronizadoEn;

    @Column(name = "wa_token_vence_en")
    private LocalDateTime waTokenVenceEn;

    @Column(name = "wa_revocado_en")
    private LocalDateTime waRevocadoEn;

    @Column(name = "wa_historial", length = 20)
    private String waHistorial;

    @Column(name = "wa_aviso", length = 400)
    private String waAviso;

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
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getRubro() { return rubro; }
    public void setRubro(String rubro) { this.rubro = rubro; }
    public String getOferta() { return oferta; }
    public void setOferta(String oferta) { this.oferta = oferta; }
    public String getDetallesProducto() { return detallesProducto; }
    public void setDetallesProducto(String detallesProducto) { this.detallesProducto = detallesProducto; }
    public String getMpTitular() { return mpTitular; }
    public void setMpTitular(String mpTitular) { this.mpTitular = mpTitular; }
    public String getMpAlias() { return mpAlias; }
    public void setMpAlias(String mpAlias) { this.mpAlias = mpAlias; }
    public String getMpCvu() { return mpCvu; }
    public void setMpCvu(String mpCvu) { this.mpCvu = mpCvu; }
    public String getMpCbu() { return mpCbu; }
    public void setMpCbu(String mpCbu) { this.mpCbu = mpCbu; }
    public String getWaPhoneNumberId() { return waPhoneNumberId; }
    public void setWaPhoneNumberId(String waPhoneNumberId) { this.waPhoneNumberId = waPhoneNumberId; }
    public String getWaWabaId() { return waWabaId; }
    public void setWaWabaId(String waWabaId) { this.waWabaId = waWabaId; }
    public String getWaAccessToken() { return waAccessToken; }
    public void setWaAccessToken(String waAccessToken) { this.waAccessToken = waAccessToken; }
    public String getWaNumero() { return waNumero; }
    public void setWaNumero(String waNumero) { this.waNumero = waNumero; }
    public String getWaSaludo() { return waSaludo; }
    public void setWaSaludo(String waSaludo) { this.waSaludo = waSaludo; }
    public boolean isWaActivo() { return waActivo; }
    public void setWaActivo(boolean waActivo) { this.waActivo = waActivo; }
    public String getWaNombre() { return waNombre; }
    public void setWaNombre(String waNombre) { this.waNombre = waNombre; }
    public String getWaTipo() { return waTipo; }
    public void setWaTipo(String waTipo) { this.waTipo = waTipo; }
    public String getWaEstado() { return waEstado; }
    public void setWaEstado(String waEstado) { this.waEstado = waEstado; }
    public LocalDateTime getWaConectadoEn() { return waConectadoEn; }
    public void setWaConectadoEn(LocalDateTime waConectadoEn) { this.waConectadoEn = waConectadoEn; }
    public LocalDateTime getWaSincronizadoEn() { return waSincronizadoEn; }
    public void setWaSincronizadoEn(LocalDateTime waSincronizadoEn) { this.waSincronizadoEn = waSincronizadoEn; }
    public LocalDateTime getWaTokenVenceEn() { return waTokenVenceEn; }
    public void setWaTokenVenceEn(LocalDateTime waTokenVenceEn) { this.waTokenVenceEn = waTokenVenceEn; }
    public LocalDateTime getWaRevocadoEn() { return waRevocadoEn; }
    public void setWaRevocadoEn(LocalDateTime waRevocadoEn) { this.waRevocadoEn = waRevocadoEn; }
    public String getWaHistorial() { return waHistorial; }
    public void setWaHistorial(String waHistorial) { this.waHistorial = waHistorial; }
    public String getWaAviso() { return waAviso; }
    public void setWaAviso(String waAviso) { this.waAviso = waAviso; }
}
