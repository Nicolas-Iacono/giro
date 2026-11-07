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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "categorias", uniqueConstraints = @UniqueConstraint(columnNames = {"comercio_id", "nombre"}))
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false)
    private Comercio comercio;

    @Column(nullable = false, length = 120)
    private String nombre;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
