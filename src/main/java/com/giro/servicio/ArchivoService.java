package com.giro.servicio;

import com.giro.error.NegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ArchivoService {
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp", "image/jpg");
    private final Path raiz;
    private final String supabaseUrl;
    private final String bucket;
    private final String clave;
    private final RestClient http = RestClient.create();
    private volatile boolean bucketPublico;

    public ArchivoService(@Value("${giro.uploads}") String uploads,
                          @Value("${giro.supabase.url}") String supabaseUrl,
                          @Value("${giro.supabase.bucket}") String bucket,
                          @Value("${giro.supabase.key:}") String clave) {
        this.raiz = Path.of(uploads).toAbsolutePath().normalize();
        this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.bucket = bucket;
        this.clave = clave == null ? "" : clave.trim();
    }

    public String guardar(Long comercioId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("Elegí una imagen");
        }
        if (clave.isBlank()) {
            throw new NegocioException("Falta GIRO_SUPABASE_KEY para guardar la imagen");
        }
        String tipo = tipo(file.getContentType());
        String extension = switch (tipo) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String objeto = comercioId + "/" + UUID.randomUUID() + extension;
        asegurarBucketPublico();
        try {
            http.post()
                    .uri(URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objeto))
                    .header("Authorization", "Bearer " + clave)
                    .header("apikey", clave)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(tipo))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new NegocioException("No se pudo guardar la imagen en Supabase (" + ex.getStatusCode().value() + ")");
        } catch (IOException ex) {
            throw new NegocioException("No se pudo leer la imagen");
        }
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objeto;
    }

    public Path resolver(String archivo) {
        Path destino = raiz.resolve(archivo).normalize();
        if (!destino.startsWith(raiz) || !Files.exists(destino)) {
            throw new NegocioException(org.springframework.http.HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        return destino;
    }

    private void asegurarBucketPublico() {
        if (bucketPublico) return;
        try {
            http.put()
                    .uri(URI.create(supabaseUrl + "/storage/v1/bucket/" + bucket))
                    .header("Authorization", "Bearer " + clave)
                    .header("apikey", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"public\":true}")
                    .retrieve()
                    .toBodilessEntity();
            bucketPublico = true;
        } catch (RestClientResponseException ignored) {
            bucketPublico = true;
        }
    }

    private static String tipo(String contentType) {
        String tipo = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        int separador = tipo.indexOf(';');
        if (separador >= 0) tipo = tipo.substring(0, separador).trim();
        if ("image/jpg".equals(tipo)) tipo = "image/jpeg";
        if (!TIPOS.contains(tipo)) {
            throw new NegocioException("La imagen tiene que ser JPG, PNG o WEBP");
        }
        return "image/jpg".equals(tipo) ? "image/jpeg" : tipo;
    }
}
