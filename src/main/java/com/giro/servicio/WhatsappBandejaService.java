package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.WhatsappContacto;
import com.giro.domain.WhatsappMensaje;
import com.giro.error.NegocioException;
import com.giro.repo.WhatsappContactoRepository;
import com.giro.repo.WhatsappMensajeRepository;
import com.giro.seguridad.Sesion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WhatsappBandejaService {
    private final Sesion sesion;
    private final WhatsappContactoRepository contactos;
    private final WhatsappMensajeRepository mensajes;
    private final WhatsappGiroService whatsapp;

    public WhatsappBandejaService(Sesion sesion, WhatsappContactoRepository contactos, WhatsappMensajeRepository mensajes, WhatsappGiroService whatsapp) {
        this.sesion = sesion;
        this.contactos = contactos;
        this.mensajes = mensajes;
        this.whatsapp = whatsapp;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> conversaciones() {
        return contactos.findByComercioIdOrderByUltimoEnDesc(sesion.comercioId()).stream().map(this::resumen).toList();
    }

    @Transactional
    public Map<String, Object> conversacion(Long id) {
        WhatsappContacto contacto = propio(id);
        contacto.setNoLeidos(0);
        Map<String, Object> body = resumen(contacto);
        body.put("mensajes", mensajes.findByContactoIdOrderByCreadoEnAscIdAsc(contacto.getId()).stream().map(this::mensaje).toList());
        return body;
    }

    @Transactional
    public Map<String, Object> enviar(Long id, Modelos.TextoWhatsapp entrada) {
        WhatsappContacto contacto = propio(id);
        String texto = entrada == null || entrada.texto() == null ? "" : entrada.texto().trim();
        if (texto.isBlank()) throw new NegocioException("Escribí un mensaje");
        if (texto.length() > 3500) throw new NegocioException("El mensaje es demasiado largo");
        whatsapp.enviarTexto(contacto.getComercio(), contacto.getTelefono(), texto);
        return conversacion(id);
    }

    @Transactional
    public Map<String, Object> bot(Long id, boolean activo) {
        WhatsappContacto contacto = propio(id);
        contacto.setBotActivo(activo);
        return resumen(contacto);
    }

    private WhatsappContacto propio(Long id) {
        return contactos.findByIdAndComercioId(id, sesion.comercioId())
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "No encontré esa conversación"));
    }

    private Map<String, Object> resumen(WhatsappContacto contacto) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", contacto.getId());
        body.put("telefono", contacto.getTelefono());
        body.put("nombre", contacto.getNombre());
        body.put("ultimoTexto", contacto.getUltimoTexto());
        body.put("ultimoEn", contacto.getUltimoEn() == null ? null : contacto.getUltimoEn().toString());
        body.put("noLeidos", contacto.getNoLeidos());
        body.put("botActivo", contacto.isBotActivo());
        return body;
    }

    private Map<String, Object> mensaje(WhatsappMensaje mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", mensaje.getId());
        body.put("direccion", mensaje.getDireccion());
        body.put("origen", mensaje.getOrigen());
        body.put("texto", mensaje.getTexto());
        body.put("estado", mensaje.getEstado());
        body.put("creadoEn", mensaje.getCreadoEn().toString());
        return body;
    }
}
