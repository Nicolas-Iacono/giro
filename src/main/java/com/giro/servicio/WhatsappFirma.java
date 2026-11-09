package com.giro.servicio;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WhatsappFirma {
    private WhatsappFirma() {}

    public static boolean valida(byte[] cuerpo, String firma, String secreto) {
        if (secreto == null || secreto.isBlank() || cuerpo == null || firma == null || !firma.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] esperada = ("sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo))).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean token(String esperado, String recibido) {
        if (esperado == null || esperado.isBlank() || recibido == null) return false;
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean esRevocacion(int code) {
        return code == 190 || code == 463 || code == 467;
    }
}
