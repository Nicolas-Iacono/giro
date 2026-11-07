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
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(length = 40)
    private String telefono;

    @Column(length = 180)
    private String email;

    @Column(length = 2000)
    private String observaciones;

    @Column(length = 32)
    private String documento;

    @Column(length = 180)
    private String direccion;

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
    private String categoria = "ESTANDAR";

    @Column(nullable = false)
    private boolean avisosWhatsapp;

    @Column(nullable = false)
    private boolean cuentaCorriente = true;

    @Column(precision = 14, scale = 2)
    private BigDecimal limiteCredito;

    private Integer plazoDias;

    @Column(nullable = false)
    private boolean avisoVencimiento;

    @Column(length = 8)
    private String talle;

    @Column(length = 8)
    private String talleCalzado;

    @Column(length = 8)
    private String tallePantalon;

    @Column(length = 80)
    private String intereses;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
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
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public boolean isAvisosWhatsapp() { return avisosWhatsapp; }
    public void setAvisosWhatsapp(boolean avisosWhatsapp) { this.avisosWhatsapp = avisosWhatsapp; }
    public boolean isCuentaCorriente() { return cuentaCorriente; }
    public void setCuentaCorriente(boolean cuentaCorriente) { this.cuentaCorriente = cuentaCorriente; }
    public BigDecimal getLimiteCredito() { return limiteCredito; }
    public void setLimiteCredito(BigDecimal limiteCredito) { this.limiteCredito = limiteCredito; }
    public Integer getPlazoDias() { return plazoDias; }
    public void setPlazoDias(Integer plazoDias) { this.plazoDias = plazoDias; }
    public boolean isAvisoVencimiento() { return avisoVencimiento; }
    public void setAvisoVencimiento(boolean avisoVencimiento) { this.avisoVencimiento = avisoVencimiento; }
    public String getTalle() { return talle; }
    public void setTalle(String talle) { this.talle = talle; }
    public String getTalleCalzado() { return talleCalzado; }
    public void setTalleCalzado(String talleCalzado) { this.talleCalzado = talleCalzado; }
    public String getTallePantalon() { return tallePantalon; }
    public void setTallePantalon(String tallePantalon) { this.tallePantalon = tallePantalon; }
    public String getIntereses() { return intereses; }
    public void setIntereses(String intereses) { this.intereses = intereses; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
