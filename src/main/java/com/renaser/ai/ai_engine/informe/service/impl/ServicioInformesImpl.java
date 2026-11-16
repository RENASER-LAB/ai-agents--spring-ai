package com.renaser.ai.ai_engine.informe.service.impl;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PostulanteDelVisor;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme.Armado;
import com.renaser.ai.ai_engine.informe.service.EtapaDelInforme;
import com.renaser.ai.ai_engine.informe.service.FormatoDelInforme;
import com.renaser.ai.ai_engine.informe.service.LoComunDelInforme;
import com.renaser.ai.ai_engine.informe.service.PdfDelInforme;
import com.renaser.ai.ai_engine.informe.service.ServicioInformes;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.FilaRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.EnlaceArchivo;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Ver {@link ServicioInformes}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioInformesImpl implements ServicioInformes {

    /** El mismo permiso con el que se firma el enlace del CV en el Excel del ranking. */
    private static final String PERMISO_CV = "descargar_entregables";
    private static final String AVISO = "LEEME - informes que faltan.txt";
    private static final String TIPO_PDF = "application/pdf";
    private static final String TIPO_ZIP = "application/zip";

    private final AlcanceSobreLaVacante alcance;
    private final ArmadoDelInforme armado;
    private final PdfDelInforme generadorPdf;
    private final ServicioPostulacionesPanel archivos;
    private final ServicioPerfilIntegralPanel tandas;
    private final NotaEtapaRepository notasEtapa;

    @Override
    public InformesDelPostulante estados(ContextoUsuario quien, Long postulacionId) {
        List<EtapaDelInforme> suyas = etapasDe(quien);
        if (suyas.isEmpty()) {
            throw new AccessDeniedException("No tienes permiso para ver ningún informe");
        }
        // Visible con cualquiera de sus permisos: lo ajeno es 404 para todos.
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, suyas.get(0).permiso());
        List<EstadoDeUnInforme> estados = new ArrayList<>();
        for (EtapaDelInforme etapa : suyas) {
            Optional<Postulacion> visible = visibleCon(quien, postulacionId, etapa);
            if (visible.isEmpty()) {
                continue;
            }
            estados.add(armado.estado(visible.get(), etapa));
        }
        return new InformesDelPostulante(postulacion.getId(), null, estados);
    }

    @Override
    public Informe informe(ContextoUsuario quien, Long postulacionId, String etapa) {
        EtapaDelInforme laEtapa = EtapaDelInforme.de(etapa);
        Postulacion postulacion = laVisible(quien, postulacionId, laEtapa);
        return conEnlaceAlCv(quien, armar(postulacion, laEtapa));
    }

    @Override
    public ArchivoArmado pdf(ContextoUsuario quien, Long postulacionId, String etapa) {
        Informe informe = informe(quien, postulacionId, etapa);
        return new ArchivoArmado(informe.nombreArchivo(), TIPO_PDF, generadorPdf.generar(informe));
    }

    @Override
    public ArchivoArmado zipDelPostulante(ContextoUsuario quien, Long postulacionId) {
        List<EtapaDelInforme> suyas = etapasDe(quien);
        if (suyas.isEmpty()) {
            throw new AccessDeniedException("No tienes permiso para ver ningún informe");
        }
        Postulacion postulacion = alcance.laPostulacionVisible(quien, postulacionId, suyas.get(0).permiso());
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        List<String> faltan = new ArrayList<>();
        String nombre = null;
        String vacante = null;
        for (EtapaDelInforme etapa : EtapaDelInforme.values()) {
            if (!suyas.contains(etapa) || visibleCon(quien, postulacionId, etapa).isEmpty()) {
                faltan.add(etapa.titulo() + ": no tienes permiso para verlo.");
                continue;
            }
            Optional<Armado> hecho = armado.armar(postulacion, etapa, true);
            if (hecho.isEmpty()) {
                faltan.add(etapa.titulo() + ": " + armado.estado(postulacion, etapa).motivo() + ".");
                continue;
            }
            Informe informe = conEnlaceAlCv(quien, hecho.get());
            nombre = informe.candidato();
            vacante = informe.vacante();
            entradas.put(etapa.enElZip(), generadorPdf.generar(informe));
        }
        if (entradas.isEmpty()) {
            throw new IllegalStateException("Todavía no hay ningún informe de este postulante: "
                    + String.join(" ", faltan));
        }
        if (!faltan.isEmpty()) {
            entradas.put(AVISO, aviso(List.of("Faltan estos informes:", ""), faltan));
        }
        String archivo = FormatoDelInforme.paraArchivo("Informes - " + nombre
                + (vacante == null ? "" : " - " + vacante)) + ".zip";
        return new ArchivoArmado(archivo, TIPO_ZIP, zip(entradas));
    }

    @Override
    public VisorDeLaVacante visor(ContextoUsuario quien, Long vacanteId) {
        List<EtapaDelInforme> suyas = etapasDe(quien);
        if (suyas.isEmpty()) {
            throw new AccessDeniedException("No tienes permiso para ver ningún informe");
        }
        RankingVacante ranking = tandas.ranking(quien, vacanteId);
        List<Long> ids = ranking.filas().stream().map(FilaRanking::postulacionId).toList();
        Map<Long, NotaEtapa> pruebas = ids.isEmpty() ? Map.of()
                : notasEtapa.findByPostulacionIdInAndEtapaCodigo(ids, "PRUEBA_PUESTO").stream()
                        .collect(Collectors.toMap(NotaEtapa::getPostulacionId, Function.identity(), (a, b) -> a));
        List<PostulanteDelVisor> postulantes = ranking.filas().stream()
                .map(f -> new PostulanteDelVisor(f.postulacionId(), f.puesto(), f.candidato(),
                        f.grupoPrioridad(),
                        suyas.contains(EtapaDelInforme.CV) ? f.notaCurriculum() : null,
                        suyas.contains(EtapaDelInforme.EVALUACION) ? f.notaEtapa() : null,
                        suyas.contains(EtapaDelInforme.PRUEBA) && pruebas.containsKey(f.postulacionId())
                                ? pruebas.get(f.postulacionId()).getPuntaje() : null))
                .toList();
        return new VisorDeLaVacante(vacanteId, ranking.vacante(), ranking.puesto(), postulantes,
                suyas.stream().map(Enum::name).toList());
    }

    @Override
    public ArchivoArmado zipDeLaVacante(ContextoUsuario quien, Long vacanteId) {
        List<EtapaDelInforme> suyas = etapasDe(quien);
        if (suyas.isEmpty()) {
            throw new AccessDeniedException("No tienes permiso para ver ningún informe");
        }
        RankingVacante ranking = tandas.ranking(quien, vacanteId);
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        List<String> faltan = new ArrayList<>();
        Set<String> carpetas = new HashSet<>();
        int conInformes = 0;
        for (FilaRanking fila : ranking.filas()) {
            String carpeta = carpetaUnica(carpetas, String.format("%02d - %s", fila.puesto(),
                    FormatoDelInforme.paraArchivo(fila.candidato())));
            boolean alguno = false;
            boolean seVe = false;
            for (EtapaDelInforme etapa : EtapaDelInforme.values()) {
                if (!suyas.contains(etapa)) {
                    continue;
                }
                Optional<Postulacion> visible = visibleCon(quien, fila.postulacionId(), etapa);
                if (visible.isEmpty()) {
                    continue;
                }
                seVe = true;
                Optional<Armado> hecho = armado.armar(visible.get(), etapa, true);
                if (hecho.isEmpty()) {
                    faltan.add(fila.candidato() + " · " + etapa.titulo() + ": "
                            + armado.estado(visible.get(), etapa).motivo() + ".");
                    continue;
                }
                entradas.put(carpeta + "/" + etapa.enElZip(), generadorPdf.generar(conEnlaceAlCv(quien, hecho.get())));
                alguno = true;
            }
            if (alguno) {
                conInformes++;
            } else if (seVe) {
                // Una carpeta por postulante visible (AC-01f2), aunque todavía no tenga ningún
                // informe: queda vacía y el aviso dice por qué.
                entradas.put(carpeta + "/", new byte[0]);
            }
        }
        if (conInformes == 0 && faltan.isEmpty()) {
            throw new IllegalStateException("Todavía no hay ningún informe en esta vacante.");
        }
        if (!faltan.isEmpty()) {
            entradas.put(AVISO, aviso(List.of("Informes que todavía no existen, y por qué:", ""), faltan));
        }
        log.info("Informes de la vacante {}: {} postulantes con informes, {} que faltan", vacanteId,
                conInformes, faltan.size());
        String archivo = FormatoDelInforme.paraArchivo("Informes - " + ranking.vacante() + " - "
                + FormatoDelInforme.fecha(Instant.now()).replace('/', '-')) + ".zip";
        return new ArchivoArmado(archivo, TIPO_ZIP, zip(entradas));
    }

    // ================================================================== permisos

    private List<EtapaDelInforme> etapasDe(ContextoUsuario quien) {
        List<EtapaDelInforme> suyas = new ArrayList<>();
        for (EtapaDelInforme etapa : EtapaDelInforme.values()) {
            if (quien.tiene(etapa.permiso())) {
                suyas.add(etapa);
            }
        }
        return suyas;
    }

    /** 403 sin el permiso de la etapa; 404 si es de otra empresa o está fuera del alcance. */
    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, EtapaDelInforme etapa) {
        if (!quien.tiene(etapa.permiso())) {
            throw new AccessDeniedException("No tienes el permiso «" + etapa.permiso() + "»");
        }
        return alcance.laPostulacionVisible(quien, postulacionId, etapa.permiso());
    }

    private Optional<Postulacion> visibleCon(ContextoUsuario quien, Long postulacionId,
                                             EtapaDelInforme etapa) {
        if (!quien.tiene(etapa.permiso())) {
            return Optional.empty();
        }
        try {
            return Optional.of(alcance.laPostulacionVisible(quien, postulacionId, etapa.permiso()));
        } catch (RuntimeException fueraDeAlcance) {
            return Optional.empty();
        }
    }

    private Armado armar(Postulacion postulacion, EtapaDelInforme etapa) {
        return armado.armar(postulacion, etapa, true).orElseThrow(() -> new IllegalStateException(
                "Todavía no hay " + etapa.titulo().toLowerCase(java.util.Locale.forLanguageTag("es"))
                        + ": " + armado.estado(postulacion, etapa).motivo() + "."));
    }

    /**
     * El enlace firmado al currículum, el mismo del Excel: abre el CV durante unas horas sin
     * sesión. Pide «descargar_entregables»; sin él, el bloque solo trae las redes.
     */
    private Informe conEnlaceAlCv(ContextoUsuario quien, Armado hecho) {
        Informe informe = hecho.informe();
        if (hecho.archivoCvId() == null || !quien.tiene(PERMISO_CV)) {
            return informe;
        }
        Optional<EnlaceArchivo> firmado;
        try {
            firmado = archivos.enlaceDeVolcado(quien, hecho.archivoCvId());
        } catch (RuntimeException noSePudo) {
            log.warn("No se pudo firmar el enlace del CV de la postulación {}: {}",
                    informe.postulacionId(), noSePudo.toString());
            return informe;
        }
        if (firmado.isEmpty() || !LoComunDelInforme.esWeb(firmado.get().url())) {
            return informe;
        }
        List<Enlace> enlaces = new ArrayList<>();
        enlaces.add(new Enlace("Currículum (PDF)", firmado.get().url(), "CV"));
        enlaces.addAll(informe.enlaces());
        return new Informe(informe.etapa(), informe.titulo(), informe.empresa(), informe.vacante(),
                informe.postulacionId(), informe.candidato(), informe.correo(), informe.generadoEn(),
                informe.nota(), informe.grupo(), informe.datosClave(), informe.recuentos(),
                informe.experiencia(), informe.resumen(), informe.evaluado(), informe.bloques(), enlaces,
                informe.detalleTitulo(), informe.detalle(), informe.detallePie(), informe.anexo(),
                informe.aviso(), informe.textosEnCamino(), informe.nombreArchivo());
    }

    // ================================================================== el .zip

    private static String carpetaUnica(Set<String> usadas, String propuesta) {
        String carpeta = propuesta;
        int n = 2;
        while (!usadas.add(carpeta)) {
            carpeta = propuesta + " (" + n++ + ")";
        }
        return carpeta;
    }

    private static byte[] aviso(List<String> cabecera, List<String> lineas) {
        List<String> todo = new ArrayList<>(cabecera);
        lineas.forEach(l -> todo.add("- " + l));
        return String.join("\r\n", todo).concat("\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entradas) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo armar el .zip de los informes", e);
        }
    }
}
