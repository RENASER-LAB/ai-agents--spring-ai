package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;

import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.notificacion.service.DireccionDelCandidato;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// El estado siguiente SE CALCULA, no se busca en una tabla de transiciones: el momento
// siguiente de la etapa actual, o el primer momento de la etapa que sigue. La decisión
// se entra por POR_CONFIRMAR, no por su primer momento: es la única excepción.
// Ver docs/03-ESTADOS-POSTULACION.md.
@Service
@RequiredArgsConstructor
@Slf4j
public class MaquinaEstados {

    // El orden de los momentos dentro de una etapa es fijo por diseño
    private static final List<String> MOMENTOS =
            List.of("POR_HABILITAR", "TURNO_CANDIDATO", "CALIFICANDO", "POR_CONFIRMAR");

    private final EstadoPostulacionRepository estados;
    private final PostulacionRepository postulaciones;
    private final TransicionEstadoRepository transiciones;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final VacanteRepository vacantes;
    private final ServicioAuditoria auditoria;
    private final ServicioCorreo correo;
    private final DireccionDelCandidato direcciones;
    private final ServicioEnlaceAcceso enlaces;

    // ============ El cálculo, puro y testeable ============

    public static Optional<EstadoPostulacion> calcularSiguiente(List<EstadoPostulacion> catalogo,
                                                                EstadoPostulacion actual) {
        if (actual.isEsFinal()) {
            return Optional.empty();
        }

        // Las etapas en su orden real, derivado del propio catálogo
        List<String> etapas = catalogo.stream()
                .filter(e -> e.getEtapaCodigo() != null)
                .sorted(Comparator.comparing(EstadoPostulacion::getOrden))
                .map(EstadoPostulacion::getEtapaCodigo)
                .distinct()
                .toList();

        // La entrada (POSTULADA) no tiene etapa: su siguiente es la entrada de la primera
        if (actual.getEtapaCodigo() == null) {
            return entradaDe(catalogo, etapas.get(0));
        }

        // ¿Hay un momento posterior dentro de la misma etapa?
        int momentoActual = MOMENTOS.indexOf(actual.getMomentoCodigo());
        Optional<EstadoPostulacion> siguienteMomento = catalogo.stream()
                .filter(e -> actual.getEtapaCodigo().equals(e.getEtapaCodigo()))
                .filter(e -> MOMENTOS.indexOf(e.getMomentoCodigo()) > momentoActual)
                .min(Comparator.comparing(e -> MOMENTOS.indexOf(e.getMomentoCodigo())));
        if (siguienteMomento.isPresent()) {
            return siguienteMomento;
        }

        // Se acabó la etapa: la entrada de la siguiente. Después de la última, nada:
        // salir de la decisión es una decisión de persona, no un avance.
        int indiceEtapa = etapas.indexOf(actual.getEtapaCodigo());
        if (indiceEtapa < 0 || indiceEtapa == etapas.size() - 1) {
            return Optional.empty();
        }
        return entradaDe(catalogo, etapas.get(indiceEtapa + 1));
    }

    private static Optional<EstadoPostulacion> entradaDe(List<EstadoPostulacion> catalogo, String etapa) {
        // La única excepción al cálculo: la decisión se entra por POR_CONFIRMAR.
        // Su TURNO_CANDIDATO existe solo para el ámbar (hito 3).
        if ("DECISION".equals(etapa)) {
            return catalogo.stream()
                    .filter(e -> etapa.equals(e.getEtapaCodigo())
                            && "POR_CONFIRMAR".equals(e.getMomentoCodigo()))
                    .findFirst();
        }
        return catalogo.stream()
                .filter(e -> etapa.equals(e.getEtapaCodigo()))
                .min(Comparator.comparing(e -> MOMENTOS.indexOf(e.getMomentoCodigo())));
    }

    /**
     * Si esta postulacion ya termino su recorrido: contratado, no continua o cerrada.
     *
     * <p>Existe para que quien vaya a moverla pueda preguntar antes en vez de chocar contra
     * la excepcion. Lo usa el cierre por plazo vencido, que barre evaluaciones sin mirar si
     * el candidato sigue en carrera.
     */
    public boolean yaTermino(Postulacion postulacion) {
        return postulacion.getEstadoCodigo() != null
                && estados.findById(postulacion.getEstadoCodigo())
                        .map(EstadoPostulacion::isEsFinal).orElse(false);
    }

    // ============ Las operaciones con efectos ============

    public Optional<EstadoPostulacion> siguiente(String codigoActual) {
        List<EstadoPostulacion> catalogo = estados.findAllByOrderByOrden();
        EstadoPostulacion actual = catalogo.stream()
                .filter(e -> e.getCodigo().equals(codigoActual))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el estado " + codigoActual));
        return calcularSiguiente(catalogo, actual);
    }

    // Toda transición pasa por aquí: valida, escribe el registro inmutable, actualiza la
    // postulación, audita, y avisa al candidato si el estado nuevo lo amerita.
    @Transactional
    public void transicionar(Postulacion postulacion, String estadoNuevoCodigo,
                             ContextoUsuario quien, String motivo,
                             boolean esSistema, boolean esPorLote, String motivoCierre) {
        EstadoPostulacion nuevo = estados.findById(estadoNuevoCodigo)
                .orElseThrow(() -> new IllegalArgumentException("No existe el estado " + estadoNuevoCodigo));

        // De un estado final no se sale. Hasta ahora esto lo comprobaba cada quien llamara
        // —y quien se olvidaba, resucitaba a un contratado o a un retirado sin que nada
        // fallara—. Comprobar el origen aquí lo hace cierto una vez y para todos: la
        // postulación de alguien que ya se fue no vuelve a la bandeja de nadie.
        if (postulacion.getEstadoCodigo() != null) {
            boolean yaTermino = estados.findById(postulacion.getEstadoCodigo())
                    .map(EstadoPostulacion::isEsFinal).orElse(false);
            if (yaTermino) {
                throw new IllegalStateException(
                        "La postulación " + postulacion.getId() + " ya terminó en «"
                                + postulacion.getEstadoCodigo() + "»: de un estado final no "
                                + "se sale, y menos hacia «" + estadoNuevoCodigo + "»");
            }
        }

        if (!esSistema && (motivo == null || motivo.isBlank())) {
            // La base también lo impide (CHECK), pero el error debe ser legible
            throw new IllegalArgumentException("Toda transición manual exige un motivo escrito");
        }

        String estadoAnterior = postulacion.getEstadoCodigo();

        transiciones.save(TransicionEstado.builder()
                .postulacionId(postulacion.getId())
                .estadoAnteriorCodigo(estadoAnterior)
                .estadoNuevoCodigo(estadoNuevoCodigo)
                .usuarioId(quien == null ? null : quien.usuarioId())
                .rolId(quien == null || quien.rolIds().isEmpty() ? null : quien.rolIds().get(0))
                .esSistema(esSistema)
                .esPorLote(esPorLote)
                .motivo(motivo)
                .ocurridaEn(Instant.now())
                .creadoEn(Instant.now())
                .build());

        postulacion.setEstadoCodigo(estadoNuevoCodigo);
        postulacion.setMovidoEn(Instant.now());
        if (nuevo.isEsFinal()) {
            postulacion.setMotivoCierre(motivoCierre);
        }
        postulaciones.save(postulacion);

        auditoria.registrar(postulacion.getOrganizacionId(), quien,
                "transicion_estado", "postulacion", postulacion.getId(),
                Map.of("estado", estadoAnterior == null ? "" : estadoAnterior),
                Map.of("estado", estadoNuevoCodigo), motivo);

        avisarAlCandidato(postulacion, nuevo, motivoCierre);
    }

    private void avisarAlCandidato(Postulacion postulacion, EstadoPostulacion nuevo, String motivoCierre) {
        String plantilla;
        if ("NO_CONTINUA".equals(nuevo.getCodigo())) {
            plantilla = "POSTULACION_NO_CONTINUA";
        } else if ("CERRADA".equals(nuevo.getCodigo())) {
            plantilla = "RETIRO_CANDIDATO".equals(motivoCierre) ? "RETIRO_CONFIRMADO" : "POSTULACION_CERRADA";
        } else if ("CANDIDATO".equals(nuevo.getEsperaA())) {
            // Le toca a él: hay que avisarle. Los estados internos no generan correo.
            plantilla = "POSTULACION_AVANZA";
        } else {
            return;
        }

        Usuario usuario = usuarios.findById(postulacion.getUsuarioId()).orElse(null);
        if (usuario == null) return;
        String nombre = personas.findById(usuario.getPersonaId())
                .map(Persona::getNombre).orElse("");
        String vacante = vacantes.findById(postulacion.getVacanteId())
                .map(Vacante::getTitulo).orElse("");

        // La direccion de la cuenta no siempre se puede entregar: a los candidatos que
        // entraron como una carpeta de curriculums se les invento una. Ver DireccionDelCandidato.
        String destino = direcciones.de(usuario, postulacion.getId());

        // Y el aviso lleva por donde entrar, no solo la noticia.
        //
        // Sin esto el correo decia «entra a tu panel» y no daba ni la direccion ni la forma:
        // a estos candidatos les creo la cuenta el cargador de curriculums, con una
        // contrasena que nadie les dijo. Diecinueve personas recibieron un aviso que no
        // podian atender, y el fallo no daba ninguna senal — el correo salio, se registro
        // como ENVIADO, y simplemente no servia para nada.
        //
        // El enlace se genera al avisar y no antes: cada uno reemplaza al anterior, asi que
        // el que vale es siempre el del ultimo correo que recibio.
        String enlace = "";
        try {
            enlace = enlaces.generarEnlace(postulacion.getId()).url();
        } catch (RuntimeException e) {
            // Que falle el enlace no puede tumbar la transicion: la postulacion ya avanzo y
            // deshacerlo por un correo seria peor. Se avisa igual, sin enlace, y queda escrito.
            log.error("No se pudo crear el enlace de acceso de la postulacion {}: {}",
                    postulacion.getId(), e.getMessage());
        }

        correo.enviar(postulacion.getOrganizacionId(), usuario.getId(), destino, plantilla,
                Map.of("nombre", nombre == null ? "" : nombre,
                       "vacante", vacante,
                       "estado", nuevo.getNombre(),
                       "enlace", enlace,
                       "codigo", String.valueOf(postulacion.getUuid())));
    }
}
