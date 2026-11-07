package com.giro.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String entrega = "LOCAL";

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

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal envio = BigDecimal.ZERO;

    @Column(name = "distancia_km", precision = 8, scale = 1)
    private BigDecimal distanciaKm;

    @Column(name = "punto_nombre", length = 160)
    private String puntoNombre;

    @Column(nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getEntrega() { return entrega; }
    public void setEntrega(String entrega) { this.entrega = entrega; }
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
    public BigDecimal getEnvio() { return envio; }
    public void setEnvio(BigDecimal envio) { this.envio = envio; }
    public BigDecimal getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(BigDecimal distanciaKm) { this.distanciaKm = distanciaKm; }
    public String getPuntoNombre() { return puntoNombre; }
    public void setPuntoNombre(String puntoNombre) { this.puntoNombre = puntoNombre; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public List<DetallePedido> getDetalles() { return detalles; }
}
