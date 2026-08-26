package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.notificacion.service.ServicioCorreo;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.parametro.entity.Parametro;
import com.renaser.ai.ai_engine.parametro.repository.ParametroRepository;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El tope mensual de IA de cada organización (pieza E).
 *
 * <p>El parámetro {@code tope_mensual_ia} es de la organización pero lo administra
 * Renaser (PUT /panel/plataforma/empresas/{id}/tope-ia); ausente = sin tope. La cuenta es
 * la suma de {@code ejecucion_ia.costo} del mes natural corriente, en hora de Lima —el
 * negocio es peruano y «este mes» significa el de su factura.
 *
 * <p>Al cruzar el <b>80%</b> se avisa una sola vez por mes al administrador de la empresa
 * y al de la plataforma. La marca de «ya avisé» es el parámetro
 * {@code aviso_tope_enviado_mes} con el YYYY-MM: {@code correo_enviado} no tiene
 * organización y buscar el aviso ahí obligaría a adivinarla por el destinatario. Al
 * <b>100%</b> la cola deja los trabajos nuevos EN_ESPERA (ver
 * {@code ColaCalificacionIaImpl}); nada falla y nada se frena para el candidato.
 *
 * <p>Vive en {@code ai/} porque es una regla de la cola —se pregunta al encolar—, y la
 * frontera de ArchUnit solo prohíbe el sentido contrario (selección → motor); el mismo
 * camino que ya recorre {@code PuenteCalificacionIa}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopeMensualIa {

    static final String PARAMETRO_TOPE = "tope_mensual_ia";
    static final String PARAMETRO_AVISO = "aviso_tope_enviado_mes";
    static final String PLANTILLA_AVISO = "TOPE_IA_AVISO";
    static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private static final BigDecimal UMBRAL_AVISO = new BigDecimal("0.80");

    private final ServicioParametros parametros;
    private final ParametroRepository filasParametro;
    private final EjecucionIaRepository ejecuciones;
    private final OrganizacionRepository organizaciones;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final UsuarioRepository usuarios;
    private final ServicioCorreo correo;

    /**
     * Si la organización agotó su cupo del mes: tope puesto y consumo ya en él o encima.
     *
     * <p>Sin tope no hay pregunta que hacer —ni suma que pagar—: la mayoría de las
     * organizaciones no tienen tope y este camino les cuesta una lectura de parámetro.
     */
    public boolean sinCupo(Long organizacionId) {
        Optional<BigDecimal> tope = topeDe(organizacionId);
        return tope.isPresent() && consumoDelMes(organizacionId).compareTo(tope.get()) >= 0;
    }

    /**
     * Manda el aviso del 80% si el consumo del mes ya cruzó el umbral y este mes nadie
     * lo ha mandado. Se llama al encolar un trabajo que SÍ tuvo cupo: es el momento en
     * que el gasto crece, y quien está por encima del 100% ya no necesita el aviso —sus
     * trabajos quedan en espera, que avisa solo.
     */
    @Transactional
    public void avisarSiCruzaElUmbral(Long organizacionId) {
        Optional<BigDecimal> tope = topeDe(organizacionId);
        if (tope.isEmpty()) {
            return;
        }
        BigDecimal consumo = consumoDelMes(organizacionId);
        if (consumo.compareTo(tope.get().multiply(UMBRAL_AVISO)) < 0) {
            return;
        }
        String mes = YearMonth.now(ZONA_LIMA).toString();
        if (mes.equals(parametros.texto(organizacionId, PARAMETRO_AVISO, null))) {
            return; // este mes ya se avisó: el aviso es una campana, no una sirena
        }
        marcarAvisado(organizacionId, mes);

        String nombre = organizaciones.findById(organizacionId)
                .map(Organizacion::getNombre).orElse("la organización " + organizacionId);
        int porcentaje = consumo.multiply(BigDecimal.valueOf(100))
                .divide(tope.get(), 0, RoundingMode.DOWN).intValue();
        Map<String, String> variables = Map.of(
                "empresa", nombre,
                "mes", mes,
                "consumo", consumo.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "tope", tope.get().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "porcentaje", String.valueOf(porcentaje));

        // Al administrador de la empresa con la plantilla de SU organización, y al de la
        // plataforma con la suya: cada correo sale firmado por quien corresponde.
        for (Usuario admin : administradoresDe(organizacionId)) {
            correo.enviar(organizacionId, admin.getId(), admin.getCorreo(), PLANTILLA_AVISO, variables);
        }
        organizaciones.findByEsPlataformaTrue()
                .filter(plataforma -> !plataforma.getId().equals(organizacionId))
                .ifPresent(plataforma -> {
                    for (Usuario admin : administradoresDe(plataforma.getId())) {
                        correo.enviar(plataforma.getId(), admin.getId(), admin.getCorreo(),
                                PLANTILLA_AVISO, variables);
                    }
                });
        log.warn("La organización {} lleva {} de su tope mensual de IA de {} ({}%): aviso enviado",
                organizacionId, consumo, tope.get(), porcentaje);
    }

    /** El tope de la organización, si lo tiene y es un número; en blanco o roto = sin tope. */
    Optional<BigDecimal> topeDe(Long organizacionId) {
        String crudo = parametros.texto(organizacionId, PARAMETRO_TOPE, null);
        if (crudo == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(crudo));
        } catch (NumberFormatException e) {
            // Un tope ilegible no puede congelar la calificación de nadie: se ignora y
            // se deja rastro para que alguien lo corrija.
            log.error("El parámetro {} de la organización {} no es un número: «{}». Se trata "
                    + "como SIN tope", PARAMETRO_TOPE, organizacionId, crudo);
            return Optional.empty();
        }
    }

    /** La suma de costo del mes natural corriente, en hora de Lima. */
    BigDecimal consumoDelMes(Long organizacionId) {
        YearMonth mes = YearMonth.now(ZONA_LIMA);
        Instant desde = mes.atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        Instant hasta = mes.plusMonths(1).atDay(1).atStartOfDay(ZONA_LIMA).toInstant();
        return ejecuciones.costoDelPeriodo(organizacionId, desde, hasta);
    }

    /**
     * Deja escrito el YYYY-MM del aviso. Editar un parámetro no crea filas
     * ({@code editarParametro}), así que esta marca se crea aquí si no existe: nace la
     * primera vez que una organización cruza su umbral.
     */
    private void marcarAvisado(Long organizacionId, String mes) {
        Parametro fila = filasParametro.findByOrganizacionIdAndCodigo(organizacionId, PARAMETRO_AVISO)
                .orElseGet(() -> Parametro.builder()
                        .organizacionId(organizacionId)
                        .codigo(PARAMETRO_AVISO)
                        .tipo("TEXTO")
                        .descripcion("Mes (YYYY-MM) en que ya salió el aviso del 80% del tope "
                                + "de IA. Lo escribe el sistema; borrarlo repite el aviso")
                        .creadoEn(Instant.now())
                        .build());
        fila.setValor(mes);
        fila.setModificadoEn(Instant.now());
        filasParametro.save(fila);
    }

    /** Los usuarios activos con el rol ADMINISTRADOR de una organización, con correo. */
    private List<Usuario> administradoresDe(Long organizacionId) {
        return roles.findByOrganizacionIdAndCodigo(organizacionId, "ADMINISTRADOR")
                .map(rol -> usuarioRoles.findByRolId(rol.getId()).stream()
                        .map(ur -> usuarios.findById(ur.getUsuarioId()).orElse(null))
                        .filter(u -> u != null && u.isEsActivo() && u.getCorreo() != null)
                        .toList())
                .orElse(List.of());
    }
}
