package com.giro.servicio;

import com.giro.error.NegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;

public final class Textos {
    private Textos() {}

    public static String slug(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new NegocioException("Elegí un enlace para la vidriera");
        }
        String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 80) {
            s = s.substring(0, 80).replaceAll("-$", "");
        }
        if (s.length() < 3) {
            throw new NegocioException("El enlace de la vidriera tiene que tener al menos 3 caracteres");
        }
        return s;
    }

    public static String email(String raw) {
        if (raw == null || !raw.contains("@")) {
            throw new NegocioException("El email no es válido");
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String sku(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    public static BigDecimal dinero(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) {
            throw new NegocioException(campo + " no puede ser negativo");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal cantidad(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new NegocioException("La cantidad tiene que ser mayor a cero");
        }
        return valor.setScale(3, RoundingMode.HALF_UP);
    }
}
