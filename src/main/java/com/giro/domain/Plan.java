package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "planes")
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precioMensual;

    @Column(nullable = false)
    private int maxProductos;

    @Column(nullable = false)
    private int maxEmpleados;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getPrecioMensual() { return precioMensual; }
    public void setPrecioMensual(BigDecimal precioMensual) { this.precioMensual = precioMensual; }
    public int getMaxProductos() { return maxProductos; }
    public void setMaxProductos(int maxProductos) { this.maxProductos = maxProductos; }
    public int getMaxEmpleados() { return maxEmpleados; }
    public void setMaxEmpleados(int maxEmpleados) { this.maxEmpleados = maxEmpleados; }
}
