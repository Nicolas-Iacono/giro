package com.giro.servicio;

import com.giro.error.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class WhatsappCifrado {
    private static final String PREFIJO = "enc:v1:";
    private static final int IV = 12;
    private final String clave;

    public WhatsappCifrado(@Value("${giro.whatsapp.token-key:}") String clave) {
        this.clave = clave == null ? "" : clave.trim();
    }

    public String cifrar(String plano) {
        if (plano == null || plano.isBlank()) return null;
        if (clave.isBlank()) throw new NegocioException("Falta GIRO_WHATSAPP_TOKEN_KEY en el servidor de Giro");
        try {
            byte[] iv = new byte[IV];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, llave(), new GCMParameterSpec(128, iv));
            byte[] cifrado = cipher.doFinal(plano.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] junto = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, junto, 0, iv.length);
            System.arraycopy(cifrado, 0, junto, iv.length, cifrado.length);
            return PREFIJO + Base64.getEncoder().encodeToString(junto);
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("No se pudo proteger el token de WhatsApp");
        }
    }

    public String leer(String guardado) {
        if (guardado == null || guardado.isBlank() || !guardado.startsWith(PREFIJO)) return guardado;
        if (clave.isBlank()) throw new NegocioException("Falta GIRO_WHATSAPP_TOKEN_KEY en el servidor de Giro");
        try {
            byte[] junto = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            if (junto.length <= IV) throw new IllegalArgumentException("token corto");
            byte[] iv = java.util.Arrays.copyOfRange(junto, 0, IV);
            byte[] cifrado = java.util.Arrays.copyOfRange(junto, IV, junto.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, llave(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cifrado), java.nio.charset.StandardCharsets.UTF_8);
        } catch (NegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NegocioException("No se pudo leer el token de WhatsApp");
        }
    }

    private SecretKeySpec llave() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(clave.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }
}
