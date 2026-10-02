package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.entity.PropuestaPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.PropuestaPreguntasRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.DatosDeLaVacanteParaIa;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/** Ver {@link PuenteRecomendador}. */
@Service
@Slf4j
public class PuenteRecomendadorImpl implements PuenteRecomendador {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TIPOS = List.of(ReglasDePuntos.ABIERTA,
            ReglasDePuntos.OPCION_UNICA, ReglasDePuntos.OPCION_MULTIPLE, ReglasDePuntos.ESCALA);

    private final PropuestaPreguntasRepository propuestas;
    private final VacanteRepository vacantes;
    private final VersionBancoRepository versionesBanco;
    private final CalificacionPorPuntos porPuntos;
    private final DatosDeLaVacanteParaIa datosDeLaVacante;
    /** La prueba técnica (V67): su estructura, para decirle a la IA lo que ya hay. */
    private final com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia calculoPrueba;

    /** El de la fase 1: el que usan sus pruebas unitarias. */
    public PuenteRecomendadorImpl(PropuestaPreguntasRepository propuestas, VacanteRepository vacantes,
                                  VersionBancoRepository versionesBanco, CalificacionPorPuntos porPuntos,
                                  DatosDeLaVacanteParaIa datosDeLaVacante) {
        this(propuestas, vacantes, versionesBanco, porPuntos, datosDeLaVacante, null);
    }

    @Autowired
    public PuenteRecomendadorImpl(PropuestaPreguntasRepository propuestas, VacanteRepository vacantes,
                                  VersionBancoRepository versionesBanco, CalificacionPorPuntos porPuntos,
                                  DatosDeLaVacanteParaIa datosDeLaVacante,
                                  com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia calculoPrueba) {
        this.propuestas = propuestas;
        this.vacantes = vacantes;
        this.versionesBanco = versionesBanco;
        this.porPuntos = porPuntos;
        this.datosDeLaVacante = datosDeLaVacante;
        this.calculoPrueba = calculoPrueba;
    }

    @Override
    @Transactional(readOnly = true)
    public InsumoRecomendador insumo(Long vacanteId) {
        PropuestaPreguntas pedida = laPedida(vacanteId);
        if (pedida == null) {
            return null;
        }
        // La organización sale de la propuesta, que se creó con la de la vacante.
        Vacante vacante = vacantes.findByIdAndOrganizacionId(vacanteId, pedida.getOrganizacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante " + vacanteId + " ya no existe"));
        VersionBanco base = versionesBanco.preguntasPropiasDe(vacanteId, "BORRADOR")
                .or(() -> versionesBanco.preguntasPropiasDe(vacanteId, "PUBLICADA"))
                .orElse(null);

        List<CriterioDelBorrador> criterios = List.of();
        List<PreguntaDelBorrador> sinCriterio = List.of();
        if (base != null) {
            CalificacionPorPuntos.Resultado r = porPuntos.calcular(base.getId(), null);
            criterios = r.criterios().stream()
                    .map(c -> new CriterioDelBorrador(c.criterio().getId(), c.criterio().getNombre(),
                            c.criterio().getQueEvalua(), c.maximo(),
                            c.preguntas().stream().map(PuenteRecomendadorImpl::comoDelBorrador).toList()))
                    .toList();
            sinCriterio = r.sinCriterio().stream().map(PuenteRecomendadorImpl::comoDelBorrador).toList();
        }
        return new InsumoRecomendador(datosDeLaVacante.de(vacante), pedida.getPuntosQueFaltan(),
                pedida.getIndicacion(), TIPOS, criterios, sinCriterio);
    }

    private static PreguntaDelBorrador comoDelBorrador(CalificacionPorPuntos.PreguntaCalculada p) {
        return new PreguntaDelBorrador(p.pregunta().getTipo(), p.pregunta().getEnunciado(), p.maximo());
    }

    @Override
    @Transactional
    public void guardarPropuesta(Long vacanteId, ResultadoRecomendador resultado) {
        PropuestaPreguntas pedida = laPedida(vacanteId);
        if (pedida == null) {
            log.warn("La propuesta de la vacante {} llegó y ya no había ninguna pedida", vacanteId);
            return;
        }
        // Los puntos se guardan como enteros: la aduana ya comprobó que lo son.
        List<CriterioPropuesto> limpia = resultado.criterios().stream()
                .map(c -> new CriterioPropuesto(c.criterioExistenteId(), c.nombre(), c.queEvalua(),
                        c.preguntas().stream()
                                .map(p -> new PreguntaPropuesta(p.tipo(), p.enunciado(),
                                        entero(p.puntos()), p.queDebeTener(),
                                        p.opciones() == null ? List.of() : p.opciones().stream()
                                                .map(o -> new OpcionPropuesta(o.texto(), entero(o.puntos())))
                                                .toList()))
                                .toList()))
                .toList();
        pedida.setContenido(JSON.writeValueAsString(limpia));
        pedida.setEstado(PropuestaPreguntas.LISTA);
        pedida.setTerminadaEn(Instant.now());
        propuestas.save(pedida);
    }

    @Override
    @Transactional
    public void marcarFallida(Long vacanteId, List<String> errores) {
        PropuestaPreguntas pedida = laPedida(vacanteId);
        if (pedida == null) {
            return;
        }
        String motivo = "La IA no devolvió una propuesta válida, ni al corregirla: "
                + String.join(" · ", errores);
        pedida.setMotivoFallo(motivo.length() > 2000 ? motivo.substring(0, 1999) + "…" : motivo);
        pedida.setEstado(PropuestaPreguntas.FALLIDA);
        pedida.setTerminadaEn(Instant.now());
        propuestas.save(pedida);
    }

    // ==================== La prueba técnica (V67) ====================

    @Override
    @Transactional(readOnly = true)
    public com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba insumoPrueba(
            Long vacanteId) {
        PropuestaPreguntas pedida = laPedidaDeLaPrueba(vacanteId);
        if (pedida == null) {
            return null;
        }
        Vacante vacante = vacantes.findByIdAndOrganizacionId(vacanteId, pedida.getOrganizacionId())
                .orElseThrow(() -> new IllegalStateException("La vacante " + vacanteId + " ya no existe"));
        VersionBanco base = versionesBanco.pruebaPropiaDe(vacanteId, "BORRADOR")
                .or(() -> versionesBanco.pruebaPropiaDe(vacanteId, "PUBLICADA"))
                .orElse(null);
        String enunciado = null;
        String modalidad = null;
        Integer minutos = null;
        Integer dias = null;
        List<com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregableDelBorrador> entregables = List.of();
        List<com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorradorDePrueba> criterios = List.of();
        if (base != null) {
            var r = calculoPrueba.estructura(base.getId());
            enunciado = base.getEnunciado();
            modalidad = base.getModalidad();
            minutos = base.getDuracionMinutos();
            dias = base.getPlazoDias();
            entregables = r.entregables().stream()
                    .map(e -> new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregableDelBorrador(
                            e.getId(), e.getNombre(), e.getFormato()))
                    .toList();
            criterios = r.criterios().stream()
                    .map(c -> new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioDelBorradorDePrueba(
                            c.criterio().getId(), c.criterio().getNombre(), c.criterio().getQueEvalua(),
                            c.sistemaMaximo(), c.calificadaMaximo(), c.calificador(),
                            c.entregables().stream().map(e -> e.getId()).toList(),
                            c.preguntas().stream().map(pc -> new PreguntaDelBorrador(
                                    pc.pregunta().getTipo(), pc.pregunta().getEnunciado(), pc.maximo()))
                                    .toList()))
                    .toList();
        }
        return new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba(
                datosDeLaVacante.de(vacante), pedida.getPuntosQueFaltan(), pedida.getIndicacion(),
                TIPOS, enunciado, modalidad, minutos, dias, entregables, criterios);
    }

    @Override
    @Transactional
    public void guardarPropuestaPrueba(Long vacanteId,
                                       com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba resultado) {
        PropuestaPreguntas pedida = laPedidaDeLaPrueba(vacanteId);
        if (pedida == null) {
            log.warn("La propuesta de prueba de la vacante {} llegó y ya no había ninguna pedida", vacanteId);
            return;
        }
        List<CriterioPropuesto> criterios = resultado.criterios().stream()
                .map(c -> new CriterioPropuesto(c.criterioExistenteId(), c.nombre(), c.queEvalua(),
                        c.preguntas() == null ? List.of() : c.preguntas().stream()
                                .map(p -> new PreguntaPropuesta(p.tipo(), p.enunciado(),
                                        ReglasDePuntos.ABIERTA.equals(p.tipo()) ? BigDecimal.ZERO : entero(p.puntos()),
                                        p.queDebeTener(),
                                        p.opciones() == null ? List.of() : p.opciones().stream()
                                                .map(o -> new OpcionPropuesta(o.texto(), entero(o.puntos())))
                                                .toList()))
                                .toList(),
                        entero(c.parteCalificada()), c.calificador(),
                        c.entregables() == null ? List.of() : c.entregables(),
                        c.entregablesExistentes() == null ? List.of() : c.entregablesExistentes()))
                .toList();
        var limpia = new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PropuestaDePrueba(
                resultado.caso(), resultado.entregables() == null ? List.of() : resultado.entregables(),
                criterios);
        pedida.setContenido(JSON.writeValueAsString(limpia));
        pedida.setEstado(PropuestaPreguntas.LISTA);
        pedida.setTerminadaEn(Instant.now());
        propuestas.save(pedida);
    }

    @Override
    @Transactional
    public void marcarFallidaPrueba(Long vacanteId, List<String> errores) {
        PropuestaPreguntas pedida = laPedidaDeLaPrueba(vacanteId);
        if (pedida == null) {
            return;
        }
        String motivo = "La IA no devolvió una propuesta válida, ni al corregirla: "
                + String.join(" · ", errores);
        pedida.setMotivoFallo(motivo.length() > 2000 ? motivo.substring(0, 1999) + "…" : motivo);
        pedida.setEstado(PropuestaPreguntas.FALLIDA);
        pedida.setTerminadaEn(Instant.now());
        propuestas.save(pedida);
    }

    private PropuestaPreguntas laPedidaDeLaPrueba(Long vacanteId) {
        return propuestas.findFirstByVacanteIdAndPropositoOrderByIdDesc(vacanteId,
                        PropuestaPreguntas.PRUEBA_PUESTO)
                .filter(p -> PropuestaPreguntas.PEDIDA.equals(p.getEstado()))
                .orElse(null);
    }

    private PropuestaPreguntas laPedida(Long vacanteId) {
        return propuestas.findFirstByVacanteIdOrderByIdDesc(vacanteId)
                .filter(p -> PropuestaPreguntas.PEDIDA.equals(p.getEstado()))
                .orElse(null);
    }

    private static BigDecimal entero(BigDecimal puntos) {
        return puntos == null ? null : puntos.setScale(0, RoundingMode.HALF_UP);
    }
}
