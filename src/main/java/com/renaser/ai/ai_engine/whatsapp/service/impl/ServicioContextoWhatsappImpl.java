package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.entity.TransicionEstado;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.TransicionEstadoRepository;
import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Accion;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Contexto;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Etapa;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Listado;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Resultado;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Resumen;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Situacion;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.VacanteResumida;
import com.renaser.ai.ai_engine.whatsapp.entity.ResultadoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.entity.VinculoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.repository.BajaWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.ResultadoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.repository.VinculoWhatsappRepository;
import com.renaser.ai.ai_engine.whatsapp.service.Celular;
import com.renaser.ai.ai_engine.whatsapp.service.FichaPublica;
import com.renaser.ai.ai_engine.whatsapp.service.LoQueLeToca;
import com.renaser.ai.ai_engine.whatsapp.service.ProcedimientoPublico;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.ReferenciasOpacas;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioContextoWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.SinAccesoWhatsappException;
import com.renaser.ai.ai_engine.whatsapp.service.TextosDeWhatsapp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ver {@link ServicioContextoWhatsapp}.
 *
 * <p>Qué es consultable lo decide una sola regla ({@link #consultables}), la misma para el listado y
 * para el detalle: así una selección antigua se valida otra vez en cada pregunta y no hay un
 * camino que deje ver algo que el otro no deja.
 *
 * <p>Una postulación es consultable desde ese número si:
 * <ul>
 *   <li>el número no escribió BAJA;</li>
 *   <li>es de la persona del vínculo, que conserva ese celular en su cuenta;</li>
 *   <li>su vacante existe (no se eliminó) y es de la misma organización que la postulación;</li>
 *   <li>y, o sigue en carrera con el vínculo ACTIVO, o terminó con el vínculo activo al terminar y
 *       su resultado sigue dentro de sus 30 días, sin acceso revocado.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ServicioContextoWhatsappImpl implements ServicioContextoWhatsapp {

    /** Las que caben en la lista del bot: la décima fila es «Ver más». */
    static final int POR_PAGINA = 9;

    static final String ACTIVA = "ACTIVA";
    static final String FINAL = "FINAL";

    static final String NUMERO_QUE_NO_VALE =
            "El teléfono tiene que venir en formato internacional (E.164), como +51987654321";

    private final VinculoWhatsappRepository vinculos;
    private final ResultadoWhatsappRepository resultados;
    private final BajaWhatsappRepository bajas;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final EstadoPostulacionRepository estados;
    private final TransicionEstadoRepository transiciones;
    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final FichaPublica ficha;
    private final LoQueLeToca acciones;
    private final PropiedadesWhatsapp propiedades;

    /** Una postulación que ese número puede consultar, con lo que hace falta para contarla. */
    private record Consultable(VinculoWhatsapp vinculo, Postulacion postulacion, Vacante vacante,
                               ResultadoWhatsapp resultado) {
        boolean esFinal() {
            return resultado != null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Listado listar(String telefono, String cursor) {
        String numero = Celular.delBot(telefono).orElse(null);
        if (numero == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        long antesDe = Long.MAX_VALUE;
        if (cursor != null) {
            OptionalLong posicion = ReferenciasOpacas.leerCursor(propiedades.getSecretoDesdeElBot(),
                    numero, cursor);
            if (posicion.isEmpty()) {
                throw new SinAccesoWhatsappException();
            }
            antesDe = posicion.getAsLong();
        }
        Instant ahora = Instant.now();
        long desde = antesDe;
        List<Consultable> restantes = consultables(numero, ahora).stream()
                .filter(c -> c.vinculo().getId() < desde)
                .toList();
        List<Consultable> pagina = restantes.stream().limit(POR_PAGINA).toList();
        String siguiente = restantes.size() > POR_PAGINA
                ? ReferenciasOpacas.cursor(propiedades.getSecretoDesdeElBot(), numero,
                        pagina.get(pagina.size() - 1).vinculo().getId())
                : null;

        Map<String, OpcionUbigeo> ciudades = ficha.ciudadesDe(pagina.stream().map(Consultable::vacante).toList());
        Map<Long, String> empresas = new HashMap<>();
        List<Resumen> resumenes = pagina.stream().map(c -> {
            Vacante vacante = c.vacante();
            String empresa = empresas.computeIfAbsent(vacante.getOrganizacionId(), o -> ficha.empresaDe(vacante));
            return new Resumen(c.vinculo().getReferencia(),
                    new VacanteResumida(tituloDe(vacante), FichaPublica.distintivoDe(vacante, empresa, ciudades)),
                    c.esFinal() ? FINAL : ACTIVA,
                    c.esFinal() ? c.resultado().getTipo() : null,
                    FichaPublica.convocatoriaDe(vacante).estado(),
                    actualizadoEn(c, ahora));
        }).toList();
        return new Listado(resumenes, siguiente, alMilisegundo(ahora));
    }

    @Override
    @Transactional(readOnly = true)
    public Contexto detalle(String telefono, String postulacionRef) {
        String numero = Celular.delBot(telefono).orElse(null);
        if (numero == null) {
            throw new IllegalArgumentException(NUMERO_QUE_NO_VALE);
        }
        Instant ahora = Instant.now();
        Consultable consultable = !ReferenciasOpacas.pareceReferencia(postulacionRef) ? null
                : consultables(numero, ahora).stream()
                        .filter(c -> postulacionRef.equals(c.vinculo().getReferencia()))
                        .findFirst()
                        .orElse(null);
        if (consultable == null) {
            throw new SinAccesoWhatsappException();
        }
        return contexto(consultable, ahora);
    }

    // ============ qué se puede consultar ============

    private List<Consultable> consultables(String numero, Instant ahora) {
        if (bajas.existsById(numero)) {
            return List.of();
        }
        List<VinculoWhatsapp> delNumero = vinculos.findByTelefono(numero);
        if (delNumero.isEmpty()) {
            return List.of();
        }
        Map<Long, ResultadoWhatsapp> resultadoPorVinculo = resultados.findByVinculoWhatsappIdIn(
                        delNumero.stream().map(VinculoWhatsapp::getId).toList()).stream()
                .filter(r -> r.esConsultable(ahora))
                .collect(Collectors.toMap(ResultadoWhatsapp::getVinculoWhatsappId, Function.identity(),
                        (a, b) -> a));
        // Los más recientes primero: es el orden del listado y del cursor.
        List<VinculoWhatsapp> candidatos = delNumero.stream()
                .filter(v -> v.estaActivo() || resultadoPorVinculo.containsKey(v.getId()))
                .sorted(Comparator.comparing(VinculoWhatsapp::getId).reversed())
                .toList();
        if (candidatos.isEmpty()) {
            return List.of();
        }
        Map<Long, Postulacion> postulacionPorId = porId(postulaciones.findAllById(
                candidatos.stream().map(VinculoWhatsapp::getPostulacionId).distinct().toList()), Postulacion::getId);
        Map<Long, Vacante> vacantePorId = porId(vacantes.findAllById(postulacionPorId.values().stream()
                .map(Postulacion::getVacanteId).filter(Objects::nonNull).distinct().toList()), Vacante::getId);
        Map<Long, Usuario> usuarioPorId = porId(usuarios.findAllById(postulacionPorId.values().stream()
                .map(Postulacion::getUsuarioId).filter(Objects::nonNull).distinct().toList()), Usuario::getId);
        Map<Long, Persona> personaPorId = porId(personas.findAllById(candidatos.stream()
                .map(VinculoWhatsapp::getPersonaId).distinct().toList()), Persona::getId);
        Set<String> finales = finales();

        Map<Long, Consultable> porPostulacion = new LinkedHashMap<>();
        for (VinculoWhatsapp vinculo : candidatos) {
            Postulacion postulacion = postulacionPorId.get(vinculo.getPostulacionId());
            if (postulacion == null || porPostulacion.containsKey(postulacion.getId())) {
                continue;
            }
            Vacante vacante = vacantePorId.get(postulacion.getVacanteId());
            if (vacante == null || vacante.getEliminadaEn() != null
                    || !Objects.equals(vacante.getOrganizacionId(), postulacion.getOrganizacionId())
                    || !esSuya(vinculo, postulacion, numero, usuarioPorId, personaPorId)) {
                continue;
            }
            boolean termino = postulacion.getEstadoCodigo() != null && finales.contains(postulacion.getEstadoCodigo());
            ResultadoWhatsapp resultado = resultadoPorVinculo.get(vinculo.getId());
            if (!termino && vinculo.estaActivo()) {
                porPostulacion.put(postulacion.getId(), new Consultable(vinculo, postulacion, vacante, null));
            } else if (termino && resultado != null && Objects.equals(resultado.getPostulacionId(), postulacion.getId())) {
                porPostulacion.put(postulacion.getId(), new Consultable(vinculo, postulacion, vacante, resultado));
            }
        }
        return new ArrayList<>(porPostulacion.values());
    }

    /**
     * La postulación es de la persona del vínculo, y esa persona conserva este celular en su
     * cuenta: con otro número ya no se consulta por el anterior.
     */
    private static boolean esSuya(VinculoWhatsapp vinculo, Postulacion postulacion, String numero,
                                  Map<Long, Usuario> usuarioPorId, Map<Long, Persona> personaPorId) {
        Usuario usuario = usuarioPorId.get(postulacion.getUsuarioId());
        Persona persona = personaPorId.get(vinculo.getPersonaId());
        return usuario != null && persona != null
                && Objects.equals(usuario.getPersonaId(), vinculo.getPersonaId())
                && numero.equals(persona.getTelefono());
    }

    // ============ el contexto de una ============

    private Contexto contexto(Consultable c, Instant ahora) {
        Postulacion postulacion = c.postulacion();
        Vacante vacante = c.vacante();
        Map<String, EstadoPostulacion> catalogo = estados.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo, Function.identity(), (a, b) -> a));
        EstadoPostulacion estado = catalogo.get(postulacion.getEstadoCodigo());
        String modalidad = acciones.modalidadDeLaPrueba(postulacion, vacante);

        List<Etapa> procedimiento = c.esFinal()
                ? ProcedimientoPublico.terminado(vacante, alcanzadas(postulacion, catalogo), modalidad)
                : ProcedimientoPublico.enMarcha(vacante, estado == null ? null : estado.getEtapaCodigo(), modalidad);

        TextosDelMomento.Texto momento = TextosDelMomento.de(postulacion.getEstadoCodigo(),
                vacante.getInstrumentoEtapaTecnica(), acciones.pruebaSinCompletar(postulacion));
        boolean enMarcha = !c.esFinal() && estado != null;
        Situacion situacion = new Situacion(c.esFinal() ? FINAL : ACTIVA,
                enMarcha ? estado.getEtapaCodigo() : null,
                enMarcha ? estado.getMomentoCodigo() : null,
                TextosDeWhatsapp.cortar(momento.titulo(), TextosDeWhatsapp.LARGO_DEL_MOMENTO),
                TextosDeWhatsapp.cortar(momento.texto(), TextosDeWhatsapp.LARGO_DE_LA_NOVEDAD),
                alMilisegundo(postulacion.getMovidoEn() != null ? postulacion.getMovidoEn()
                        : postulacion.getCreadoEn()));

        Accion accion = c.esFinal() ? null
                : acciones.de(postulacion, vacante, ahora, propiedades.getEnlaceMisProcesos());
        Resultado resultado = c.esFinal() ? resultadoDe(c.resultado()) : null;
        Instant actualizado = actualizadoEn(c, ahora);
        return new Contexto(c.vinculo().getReferencia(), alMilisegundo(ahora), actualizado,
                actualizado.toEpochMilli(), ficha.de(vacante), procedimiento, situacion, accion, resultado);
    }

    /** El resultado como lo cuenta EX: sin explicación mientras no haya una causa pública registrada. */
    private static Resultado resultadoDe(ResultadoWhatsapp resultado) {
        return new Resultado(resultado.getTipo(), alMilisegundo(resultado.getFecha()), resultado.getRevision(),
                null, resultado.isEsCorreccion(), alMilisegundo(resultado.getConsultableHasta()));
    }

    /** Las etapas por las que pasó, leídas del historial: de las demás no se dice nada. */
    private Set<String> alcanzadas(Postulacion postulacion, Map<String, EstadoPostulacion> catalogo) {
        Set<String> etapas = new HashSet<>();
        for (TransicionEstado paso : transiciones.findByPostulacionIdOrderByOcurridaEnAsc(postulacion.getId())) {
            for (String codigo : new String[] {paso.getEstadoAnteriorCodigo(), paso.getEstadoNuevoCodigo()}) {
                EstadoPostulacion estado = codigo == null ? null : catalogo.get(codigo);
                if (estado != null && estado.getEtapaCodigo() != null) {
                    etapas.add(estado.getEtapaCodigo());
                }
            }
        }
        return etapas;
    }

    /**
     * La fecha real del último cambio que el contexto cuenta —no la de la consulta—. Es también
     * la revisión del contexto (en milisegundos): solo crece, porque cada fuente solo avanza.
     *
     * <ul>
     *   <li>la postulación: nació o cambió de estado (situación y procedimiento);</li>
     *   <li>la vacante: publicada, cerrada, el sueldo, y cualquier otro cambio de lo que cuenta
     *       —la ficha, los requisitos indispensables, la empresa, el procedimiento y los plazos de
     *       la prueba—, que la base fecha en {@code contexto_actualizado_en} (V77, QA-C0-F1);</li>
     *   <li>la acción mientras sigue en carrera: el intento y las evaluaciones, y el vencimiento
     *       de un plazo ({@link LoQueLeToca#ultimoCambio}). Terminada ya no hay acción: lo que
     *       pase después con su prueba no cambia nada de lo que se cuenta;</li>
     *   <li>y el resultado.</li>
     * </ul>
     *
     * <p>Nunca posterior a la consulta: si el reloj de la base va unos milisegundos por delante
     * del de la aplicación, se queda en el momento de la consulta.
     */
    private Instant actualizadoEn(Consultable c, Instant ahora) {
        Postulacion postulacion = c.postulacion();
        Vacante vacante = c.vacante();
        Instant ultimo = LoQueLeToca.elMasReciente(postulacion.getCreadoEn(), postulacion.getMovidoEn(),
                vacante.getPublicadaEn(), vacante.getCerradaEn(), vacante.getRemuneracionActualizadaEn(),
                vacante.getContextoActualizadoEn(),
                c.esFinal() ? null : acciones.ultimoCambio(postulacion, vacante, ahora),
                c.resultado() == null ? null : c.resultado().getActualizadoEn());
        if (ultimo == null) {
            ultimo = Instant.EPOCH;
        } else if (ultimo.isAfter(ahora)) {
            ultimo = ahora;
        }
        return alMilisegundo(ultimo);
    }

    // ============ ayudas ============

    private static String tituloDe(Vacante vacante) {
        return TextosDeWhatsapp.cortar(vacante.getTitulo() == null || vacante.getTitulo().isBlank()
                ? "Vacante" : vacante.getTitulo(), TextosDeWhatsapp.LARGO_DE_LA_VACANTE);
    }

    private Set<String> finales() {
        return estados.findAllByOrderByOrden().stream()
                .filter(EstadoPostulacion::isEsFinal)
                .map(EstadoPostulacion::getCodigo)
                .collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> porId(List<T> filas, Function<T, Long> id) {
        return filas.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static Instant alMilisegundo(Instant momento) {
        return momento == null ? null : momento.truncatedTo(ChronoUnit.MILLIS);
    }
}
