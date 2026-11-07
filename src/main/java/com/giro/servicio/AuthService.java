package com.giro.servicio;

import com.giro.api.Modelos;
import com.giro.domain.Comercio;
import com.giro.domain.EstadoSuscripcion;
import com.giro.domain.RolComercio;
import com.giro.domain.Suscripcion;
import com.giro.domain.Usuario;
import com.giro.domain.Vidriera;
import com.giro.error.NegocioException;
import com.giro.repo.ComercioRepository;
import com.giro.repo.PlanRepository;
import com.giro.repo.SuscripcionRepository;
import com.giro.repo.UsuarioRepository;
import com.giro.repo.VidrieraRepository;
import com.giro.seguridad.JwtService;
import com.giro.seguridad.Sesion;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private final ComercioRepository comercios;
    private final UsuarioRepository usuarios;
    private final PlanRepository planes;
    private final SuscripcionRepository suscripciones;
    private final VidrieraRepository vidrieras;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Sesion sesion;

    public AuthService(ComercioRepository comercios, UsuarioRepository usuarios, PlanRepository planes,
                       SuscripcionRepository suscripciones, VidrieraRepository vidrieras,
                       PasswordEncoder encoder, JwtService jwt, Sesion sesion) {
        this.comercios = comercios;
        this.usuarios = usuarios;
        this.planes = planes;
        this.suscripciones = suscripciones;
        this.vidrieras = vidrieras;
        this.encoder = encoder;
        this.jwt = jwt;
        this.sesion = sesion;
    }

    @Transactional
    public Map<String, Object> registrar(Modelos.Registro entrada) {
        String email = Textos.email(entrada.email());
        String slug = Textos.slug(entrada.slug());
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ese email ya tiene una cuenta");
        }
        if (comercios.existsBySlug(slug)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ese enlace de vidriera ya está usado");
        }
        var plan = planes.findByCodigo("INICIO")
                .orElseThrow(() -> new NegocioException("Falta el plan inicial"));

        Comercio comercio = new Comercio();
        comercio.setNombre(entrada.nombreComercio().trim());
        comercio.setSlug(slug);
        comercios.save(comercio);

        Usuario dueno = nuevoUsuario(comercio, email, entrada.password(), entrada.nombre(), RolComercio.DUENO);
        usuarios.save(dueno);

        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setComercio(comercio);
        suscripcion.setPlan(plan);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setVenceEn(LocalDate.now(Sesion.ZONA).plusDays(30));
        suscripciones.save(suscripcion);

        Vidriera vidriera = new Vidriera();
        vidriera.setComercio(comercio);
        vidriera.setTitulo(comercio.getNombre());
        vidriera.setDescripcion("");
        vidriera.setColor("#6d28d9");
        vidriera.setPublicada(true);
        vidrieras.save(vidriera);

        return sesionDe(dueno);
    }

    @Transactional
    public Map<String, Object> login(Modelos.Login entrada) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(Textos.email(entrada.email()))
                .filter(u -> encoder.matches(entrada.password(), u.getPassword()))
                .filter(Usuario::isEnabled)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos"));
        return sesionDe(usuario);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> yo() {
        return perfil(sesion.usuario());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> renovar() {
        return sesionDe(sesion.usuario());
    }

    @Transactional
    public Map<String, Object> crearEmpleado(Modelos.Empleado entrada) {
        sesion.exigirGestion();
        sesion.exigirSuscripcion();
        Usuario actual = sesion.usuario();
        RolComercio rol = parseRol(entrada.rol());
        if (rol == RolComercio.DUENO) {
            throw new NegocioException("No se puede crear otro dueño");
        }
        var suscripcion = suscripciones.findByComercioId(actual.getComercio().getId()).orElseThrow();
        if (usuarios.countByComercioIdAndActivoTrue(actual.getComercio().getId()) >= suscripcion.getPlan().getMaxEmpleados()) {
            throw new NegocioException("El plan no admite más personas en el equipo");
        }
        String email = Textos.email(entrada.email());
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ese email ya tiene una cuenta");
        }
        Usuario empleado = nuevoUsuario(actual.getComercio(), email, entrada.password(), entrada.nombre(), rol);
        usuarios.save(empleado);
        return perfil(empleado);
    }

    @Transactional(readOnly = true)
    public Object equipo() {
        sesion.exigirGestion();
        return usuarios.findByComercioIdOrderByNombreAsc(sesion.comercioId()).stream().map(this::perfil).toList();
    }

    private Usuario nuevoUsuario(Comercio comercio, String email, String password, String nombre, RolComercio rol) {
        Usuario usuario = new Usuario();
        usuario.setComercio(comercio);
        usuario.setEmail(email);
        usuario.setPasswordHash(encoder.encode(password));
        usuario.setNombre(nombre.trim());
        usuario.setRol(rol);
        return usuario;
    }

    private RolComercio parseRol(String raw) {
        try {
            return RolComercio.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new NegocioException("El rol tiene que ser ADMINISTRADOR, EMPLEADO o CAJERO");
        }
    }

    private Map<String, Object> sesionDe(Usuario usuario) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", jwt.emitir(usuario));
        body.put("usuario", perfil(usuario));
        return body;
    }

    private Map<String, Object> perfil(Usuario usuario) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", usuario.getId());
        body.put("nombre", usuario.getNombre());
        body.put("email", usuario.getEmail());
        body.put("rol", usuario.getRol().name());
        body.put("comercioId", usuario.getComercio().getId());
        body.put("comercio", usuario.getComercio().getNombre());
        body.put("slug", usuario.getComercio().getSlug());
        return body;
    }
}
