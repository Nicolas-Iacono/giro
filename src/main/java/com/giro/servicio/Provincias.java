package com.giro.servicio;

import com.giro.error.NegocioException;

import java.util.List;
import java.util.Locale;

public final class Provincias {
    private Provincias() {}

    public static final List<String> TODAS = List.of(
            "Buenos Aires",
            "Ciudad Autónoma de Buenos Aires",
            "Catamarca",
            "Chaco",
            "Chubut",
            "Córdoba",
            "Corrientes",
            "Entre Ríos",
            "Formosa",
            "Jujuy",
            "La Pampa",
            "La Rioja",
            "Mendoza",
            "Misiones",
            "Neuquén",
            "Río Negro",
            "Salta",
            "San Juan",
            "San Luis",
            "Santa Cruz",
            "Santa Fe",
            "Santiago del Estero",
            "Tierra del Fuego",
            "Tucumán");

    public static String canon(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        return TODAS.stream()
                .filter(item -> item.equalsIgnoreCase(limpio))
                .findFirst()
                .orElseThrow(() -> new NegocioException("Elegí una provincia"));
    }

    public static String codigoPostal(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (!limpio.matches("\\d{4}") && !limpio.matches("[A-Z]\\d{4}[A-Z]{3}")) {
            throw new NegocioException("El código postal tiene que tener 4 números, o una letra, 4 números y 3 letras");
        }
        return limpio;
    }
}
