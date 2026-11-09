package com.giro.api;

import com.giro.domain.MedioPago;
import com.giro.domain.TipoMovimientoCaja;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Modelos {
    private Modelos() {}

    public record Registro(
            @NotBlank String nombreComercio,
            @NotBlank String slug,
            @Email @NotBlank String email,
            @Size(min = 8, max = 80) String password,
            @NotBlank String nombre) {}

    public record Login(@Email @NotBlank String email, @NotBlank String password) {}

    public record Empleado(
            @Email @NotBlank String email,
            @Size(min = 8, max = 80) String password,
            @NotBlank String nombre,
            @NotBlank String rol) {}

    public record CategoriaEntrada(@NotBlank String nombre) {}

    public record ProductoEntrada(
            @NotBlank String nombre,
            String descripcion,
            String sku,
            Long categoriaId,
            @NotNull @DecimalMin("0.00") BigDecimal costo,
            @NotNull @DecimalMin("0.00") BigDecimal precio,
            @NotNull @DecimalMin("0.000") BigDecimal stock,
            @NotNull @DecimalMin("0.000") BigDecimal stockMinimo,
            boolean publicado,
            boolean usaVariantes,
            String publico,
            String marca,
            String modelo,
            String color,
            String material,
            String medidas,
            String peso) {}

    public record VarianteEntrada(
            @NotBlank String nombre,
            String sku,
            BigDecimal precio,
            @NotNull @DecimalMin("0.000") BigDecimal stock) {}

    public record PromoEntrada(
            @NotNull @DecimalMin("0.00") BigDecimal precioPromo,
            LocalDate desde,
            LocalDate hasta) {}

    public record ClienteEntrada(
            @NotBlank String nombre,
            String telefono,
            String email,
            String observaciones,
            String documento,
            String direccion,
            String altura,
            String localidad,
            String partido,
            String provincia,
            String codigoPostal,
            String categoria,
            Boolean avisosWhatsapp,
            Boolean cuentaCorriente,
            BigDecimal limiteCredito,
            Integer plazoDias,
            Boolean avisoVencimiento,
            String talle,
            String talleCalzado,
            String tallePantalon,
            List<String> intereses) {}

    public record Cobro(@NotNull @DecimalMin("0.01") BigDecimal monto, @NotNull MedioPago medio) {}

    public record Item(
            @NotNull Long productoId,
            Long varianteId,
            @NotNull @DecimalMin("0.001") BigDecimal cantidad) {}

    public record VentaEntrada(Long clienteId, @NotNull MedioPago medio, @NotEmpty List<Item> items) {}

    public record PedidoEntrada(
            Long clienteId,
            @NotEmpty List<Item> items,
            String entrega,
            String direccion,
            String altura,
            String localidad,
            String partido,
            String provincia,
            String codigoPostal) {}

    public record ConfirmarPedido(@NotNull MedioPago medio) {}

    public record AbrirCaja(@NotNull @DecimalMin("0.00") BigDecimal montoInicial) {}

    public record MovimientoCajaEntrada(
            @NotNull TipoMovimientoCaja tipo,
            @NotBlank String concepto,
            @NotNull @DecimalMin("0.01") BigDecimal monto) {}

    public record CerrarCaja(@NotNull @DecimalMin("0.00") BigDecimal montoContado) {}

    public record VidrieraEntrada(
            @NotBlank String titulo,
            String descripcion,
            @NotBlank String color,
            boolean publicada,
            @NotBlank String tema,
            @NotBlank String fondo,
            @NotBlank String texto,
            @NotBlank String catalogo,
            int radio,
            boolean cuotas,
            String marquesina,
            @NotBlank String hero,
            @NotBlank String fuente,
            String menu,
            String logoForma,
            Boolean mostrarRubros,
            String rubros,
            String rubroForma,
            String rubroColor,
            String secciones,
            String estado,
            String etiqueta,
            String sello,
            String heroEtiqueta,
            String heroTitulo,
            String heroTexto,
            String heroCta,
            String buscar,
            String aviso,
            String explorar,
            String sumar,
            String sumarForma,
            String sumarColor,
            Boolean sumarVisible) {}

    public record CotizarEnvio(
            String direccion,
            String altura,
            String localidad,
            String partido,
            String provincia,
            String codigoPostal) {}

    public record TarifaEnvio(
            String direccion,
            String altura,
            String localidad,
            String partido,
            String provincia,
            String codigoPostal,
            @NotNull @DecimalMin("0.00") BigDecimal base,
            @NotNull @DecimalMin("0.00") BigDecimal porKm) {}

    public record AjustesEntrada(
            @NotBlank @Size(max = 160) String nombre,
            String calle,
            String altura,
            String localidad,
            String partido,
            String provincia,
            String codigoPostal,
            @NotBlank String canal,
            List<String> rubros,
            @NotBlank String oferta,
            List<String> detalles,
            String mpTitular,
            String mpAlias,
            String mpCvu,
            String mpCbu,
            String waPhoneNumberId,
            String waWabaId,
            String waAccessToken,
            String waSaludo,
            boolean waActivo,
            boolean desconectarWhatsapp) {}

    public record ProbarWhatsapp(String phoneNumberId, String accessToken) {}
    public record SuscribirWhatsapp(String wabaId, String accessToken) {}

    public record ConectarWhatsapp(String code, String phoneNumberId, String wabaId, String onboardingType, String state) {}

    public record TextoWhatsapp(@NotBlank @Size(max = 3500) String texto) {}

    public record BotWhatsapp(boolean activo) {}
}
