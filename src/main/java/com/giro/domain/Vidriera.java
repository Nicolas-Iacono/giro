package com.giro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vidrieras")
public class Vidriera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comercio_id", nullable = false, unique = true)
    private Comercio comercio;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(length = 2000)
    private String descripcion;

    @Column(nullable = false, length = 20)
    private String color = "#6d28d9";

    @Column(nullable = false)
    private boolean publicada = true;

    @Column(nullable = false, length = 20)
    private String tema = "giro";

    @Column(nullable = false, length = 20)
    private String fondo = "blanco";

    @Column(nullable = false, length = 20)
    private String texto = "oscuro";

    @Column(nullable = false, length = 20)
    private String catalogo = "grilla";

    @Column(nullable = false)
    private int radio = 16;

    @Column(nullable = false)
    private boolean cuotas;

    @Column(nullable = false, length = 180)
    private String marquesina = "";

    @Column(nullable = false, length = 20)
    private String hero = "gradiente";

    @Column(nullable = false, length = 20)
    private String fuente = "jakarta";

    @Column(length = 500)
    private String portadaUrl;

    @Column(length = 500)
    private String logoUrl;

    @Column(nullable = false, length = 20)
    private String menu = "lados";

    @Column(nullable = false, length = 20)
    private String logoForma = "circular";

    @Column(columnDefinition = "text")
    private String portadas;

    @Column(nullable = false)
    private boolean mostrarRubros;

    @Column(length = 200)
    private String rubros = "";

    @Column(nullable = false, length = 20)
    private String rubroForma = "circular";

    @Column(length = 7)
    private String rubroColor;

    @Column(nullable = false, length = 120)
    private String estado = "";

    @Column(nullable = false, length = 80)
    private String etiqueta = "";

    @Column(nullable = false, length = 80)
    private String sello = "";

    @Column(nullable = false, length = 80)
    private String heroEtiqueta = "";

    @Column(nullable = false, length = 140)
    private String heroTitulo = "";

    @Column(nullable = false, length = 240)
    private String heroTexto = "";

    @Column(nullable = false, length = 40)
    private String heroCta = "";

    @Column(nullable = false, length = 80)
    private String buscar = "";

    @Column(nullable = false, length = 180)
    private String aviso = "";

    @Column(nullable = false, length = 60)
    private String explorar = "";

    @Column(nullable = false, length = 24)
    private String sumar = "";

    @Column(name = "sumar_forma", nullable = false, length = 20)
    private String sumarForma = "circular";

    @Column(name = "sumar_color", length = 7)
    private String sumarColor;

    @Column(name = "sumar_visible", nullable = false)
    private boolean sumarVisible = true;

    @Column(columnDefinition = "text")
    private String secciones;

    public Long getId() { return id; }
    public Comercio getComercio() { return comercio; }
    public void setComercio(Comercio comercio) { this.comercio = comercio; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isPublicada() { return publicada; }
    public void setPublicada(boolean publicada) { this.publicada = publicada; }
    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }
    public String getFondo() { return fondo; }
    public void setFondo(String fondo) { this.fondo = fondo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getCatalogo() { return catalogo; }
    public void setCatalogo(String catalogo) { this.catalogo = catalogo; }
    public int getRadio() { return radio; }
    public void setRadio(int radio) { this.radio = radio; }
    public boolean isCuotas() { return cuotas; }
    public void setCuotas(boolean cuotas) { this.cuotas = cuotas; }
    public String getMarquesina() { return marquesina; }
    public void setMarquesina(String marquesina) { this.marquesina = marquesina; }
    public String getHero() { return hero; }
    public void setHero(String hero) { this.hero = hero; }
    public String getFuente() { return fuente; }
    public void setFuente(String fuente) { this.fuente = fuente; }
    public String getPortadaUrl() { return portadaUrl; }
    public void setPortadaUrl(String portadaUrl) { this.portadaUrl = portadaUrl; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getMenu() { return menu; }
    public void setMenu(String menu) { this.menu = menu; }
    public String getLogoForma() { return logoForma; }
    public void setLogoForma(String logoForma) { this.logoForma = logoForma; }
    public String getPortadas() { return portadas; }
    public void setPortadas(String portadas) { this.portadas = portadas; }
    public boolean isMostrarRubros() { return mostrarRubros; }
    public void setMostrarRubros(boolean mostrarRubros) { this.mostrarRubros = mostrarRubros; }
    public String getRubros() { return rubros; }
    public void setRubros(String rubros) { this.rubros = rubros; }
    public String getRubroForma() { return rubroForma; }
    public void setRubroForma(String rubroForma) { this.rubroForma = rubroForma; }
    public String getRubroColor() { return rubroColor; }
    public void setRubroColor(String rubroColor) { this.rubroColor = rubroColor; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public String getSello() { return sello; }
    public void setSello(String sello) { this.sello = sello; }
    public String getHeroEtiqueta() { return heroEtiqueta; }
    public void setHeroEtiqueta(String heroEtiqueta) { this.heroEtiqueta = heroEtiqueta; }
    public String getHeroTitulo() { return heroTitulo; }
    public void setHeroTitulo(String heroTitulo) { this.heroTitulo = heroTitulo; }
    public String getHeroTexto() { return heroTexto; }
    public void setHeroTexto(String heroTexto) { this.heroTexto = heroTexto; }
    public String getHeroCta() { return heroCta; }
    public void setHeroCta(String heroCta) { this.heroCta = heroCta; }
    public String getBuscar() { return buscar; }
    public void setBuscar(String buscar) { this.buscar = buscar; }
    public String getAviso() { return aviso; }
    public void setAviso(String aviso) { this.aviso = aviso; }
    public String getExplorar() { return explorar; }
    public void setExplorar(String explorar) { this.explorar = explorar; }
    public String getSumar() { return sumar; }
    public void setSumar(String sumar) { this.sumar = sumar; }
    public String getSumarForma() { return sumarForma; }
    public void setSumarForma(String sumarForma) { this.sumarForma = sumarForma; }
    public String getSumarColor() { return sumarColor; }
    public void setSumarColor(String sumarColor) { this.sumarColor = sumarColor; }
    public boolean isSumarVisible() { return sumarVisible; }
    public void setSumarVisible(boolean sumarVisible) { this.sumarVisible = sumarVisible; }
    public String getSecciones() { return secciones; }
    public void setSecciones(String secciones) { this.secciones = secciones; }
}
