package com.renaser.ai.ai_engine.informe.service.impl;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Anexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Celular;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Enlace;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EntregableAnexo;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.EstadoDeUnInforme;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.PostulanteDelVisor;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme;
import com.renaser.ai.ai_engine.informe.service.ArmadoDelInforme.Armado;
import com.renaser.ai.ai_engine.informe.service.CelularesDelVisor;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final CelularesDelVisor celulares;

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
        return conEnlaces(quien, armar(postulacion, laEtapa));
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
            Informe informe = conEnlaces(quien, hecho.get());
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
        Orden orden = orden(quien, vacanteId);
        Map<Long, Celular> suyos = orden.filas().isEmpty() ? Map.of()
                : celulares.de(orden.filas().stream().map(o -> o.fila().postulacionId()).toList());
        List<PostulanteDelVisor> postulantes = new ArrayList<>();
        for (int i = 0; i < orden.filas().size(); i++) {
            Ordenado o = orden.filas().get(i);
            FilaRanking f = o.fila();
            // El orden ya se calculó con las tres notas; aquí solo se tapa la que no se puede ver.
            postulantes.add(new PostulanteDelVisor(f.postulacionId(), i + 1, f.candidato(),
                    f.grupoPrioridad(),
                    suyas.contains(EtapaDelInforme.CV) ? f.notaCurriculum() : null,
                    suyas.contains(EtapaDelInforme.EVALUACION) ? f.notaEtapa() : null,
                    suyas.contains(EtapaDelInforme.PRUEBA) ? o.notaPrueba() : null,
                    suyos.get(f.postulacionId())));
        }
        return new VisorDeLaVacante(vacanteId, orden.ranking().vacante(), orden.ranking().puesto(),
                postulantes, suyas.stream().map(Enum::name).toList());
    }

    @Override
    public ArchivoArmado zipDeLaVacante(ContextoUsuario quien, Long vacanteId) {
        List<EtapaDelInforme> suyas = etapasDe(quien);
        if (suyas.isEmpty()) {
            throw new AccessDeniedException("No tienes permiso para ver ningún informe");
        }
        Orden orden = orden(quien, vacanteId);
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        List<String> faltan = new ArrayList<>();
        Set<String> carpetas = new HashSet<>();
        int conInformes = 0;
        for (int i = 0; i < orden.filas().size(); i++) {
            FilaRanking fila = orden.filas().get(i).fila();
            // Numeradas con el mismo lugar que el visor (AC-11), no con el puesto del ranking.
            String carpeta = carpetaUnica(carpetas, String.format("%02d - %s", i + 1,
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
                entradas.put(carpeta + "/" + etapa.enElZip(), generadorPdf.generar(conEnlaces(quien, hecho.get())));
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
        String archivo = FormatoDelInforme.paraArchivo("Informes - " + orden.ranking().vacante() + " - "
                + FormatoDelInforme.fecha(Instant.now()).replace('/', '-')) + ".zip";
        return new ArchivoArmado(archivo, TIPO_ZIP, zip(entradas));
    }

    // ================================================================== el orden

    /** Un postulante con lo que decide su lugar: la nota de la prueba y la combinada. */
    private record Ordenado(FilaRanking fila, BigDecimal notaPrueba, BigDecimal combinada) {

        /** CV, evaluación y prueba calificada, y una vacante con pesos para combinarlas. */
        boolean tieneLasTres() {
            return fila.notaCurriculum() != null && fila.notaEtapa() != null && notaPrueba != null
                    && combinada != null;
        }
    }

    private record Orden(RankingVacante ranking, List<Ordenado> filas) {
    }

    /**
     * El orden del visor y de su .zip (spec la-ia-sabe-que-dia-es-hoy).
     *
     * <p><b>Arriba</b>, quienes tienen las tres notas —CV, evaluación y la prueba del puesto
     * calificada—, de mayor a menor por el ponderado de lo ya rendido del RF-155; a igual
     * ponderado, por la nota de la prueba y luego por la de la evaluación. <b>Debajo</b>, los
     * demás en el orden del ranking. Una prueba empezada sin calificar no es tercera nota, y una
     * vacante sin pesos de etapa no tiene ponderado: queda en el orden del ranking, como antes.
     *
     * <p>⚠️ <b>Se calcula con las tres notas aunque quien mira no pueda ver alguna</b>: el orden
     * es el mismo para todos, y la nota que no se ve sale «–» en la lista.
     */
    private Orden orden(ContextoUsuario quien, Long vacanteId) {
        RankingVacante ranking = tandas.ranking(quien, vacanteId);
        List<Long> ids = ranking.filas().stream().map(FilaRanking::postulacionId).toList();
        if (ids.isEmpty()) {
            return new Orden(ranking, List.of());
        }
        Map<Long, BigDecimal> pruebas = notasEtapa.findByPostulacionIdInAndEtapaCodigo(ids, "PRUEBA_PUESTO")
                .stream()
                .filter(n -> n.getPuntaje() != null)
                .collect(Collectors.toMap(NotaEtapa::getPostulacionId, NotaEtapa::getPuntaje, (a, b) -> a));
        Map<Long, BigDecimal> combinadas = tandas.ponderadosDeLoRendido(quien, vacanteId);
        List<Ordenado> arriba = new ArrayList<>();
        List<Ordenado> debajo = new ArrayList<>();
        for (FilaRanking f : ranking.filas()) {
            Ordenado o = new Ordenado(f, pruebas.get(f.postulacionId()),
                    combinadas == null ? null : combinadas.get(f.postulacionId()));
            (o.tieneLasTres() ? arriba : debajo).add(o);
        }
        // List.sort es estable: a igualdad completa se respeta el orden del ranking.
        arriba.sort(Comparator.comparing(Ordenado::combinada, Comparator.reverseOrder())
                .thenComparing(Ordenado::notaPrueba, Comparator.reverseOrder())
                .thenComparing(o -> o.fila().notaEtapa(), Comparator.reverseOrder()));
        List<Ordenado> todos = new ArrayList<>(arriba);
        todos.addAll(debajo);
        return new Orden(ranking, todos);
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
     * Lo que abre el informe a quien tiene «descargar_entregables»: el enlace firmado al
     * currículum —el mismo del Excel, que abre el CV unas horas sin sesión— y el de cada
     * entregable entregado. Sin ese permiso el bloque solo trae las redes y los entregables van
     * sin nada con que abrirlos, como siempre.
     *
     * <p>Es la única salida del informe —la página, el PDF y los dos .zip pasan por aquí—, así
     * que es también donde se quita lo que el armado trae para abrir un entregable cuando quien
     * mira no puede.
     */
    private Informe conEnlaces(ContextoUsuario quien, Armado hecho) {
        Informe informe = hecho.informe();
        boolean puedeAbrir = quien.tiene(PERMISO_CV);
        List<Enlace> enlaces = informe.enlaces();
        Optional<String> cv = puedeAbrir && hecho.archivoCvId() != null
                ? firmado(quien, hecho.archivoCvId(), informe.postulacionId(), "el CV") : Optional.empty();
        if (cv.isPresent()) {
            enlaces = new ArrayList<>();
            enlaces.add(new Enlace("Currículum (PDF)", cv.get(), "CV"));
            enlaces.addAll(informe.enlaces());
        }
        return new Informe(informe.etapa(), informe.titulo(), informe.empresa(), informe.vacante(),
                informe.postulacionId(), informe.candidato(), informe.correo(), informe.generadoEn(),
                informe.nota(), informe.grupo(), informe.datosClave(), informe.recuentos(),
                informe.experiencia(), informe.resumen(), informe.evaluado(), informe.bloques(), enlaces,
                informe.detalleTitulo(), informe.detalle(), informe.detallePie(),
                conEntregables(quien, informe, puedeAbrir), informe.aviso(), informe.textosEnCamino(),
                informe.nombreArchivo());
    }

    /**
     * Los entregables con lo que los abre, o sin nada si no se pueden abrir.
     *
     * <p>Un archivo lleva su id —el visor pide con él el enlace firmado de siempre— y, para el
     * PDF, el enlace firmado de 8 horas, el mismo trato que el del CV. Un entregable de tipo
     * enlace lleva la dirección que pegó el candidato, solo si es web. Uno no entregado, nada.
     */
    private Anexo conEntregables(ContextoUsuario quien, Informe informe, boolean puedeAbrir) {
        Anexo anexo = informe.anexo();
        if (anexo == null || anexo.entregables() == null || anexo.entregables().isEmpty()) {
            return anexo;
        }
        List<EntregableAnexo> entregables = new ArrayList<>();
        for (EntregableAnexo e : anexo.entregables()) {
            EntregableAnexo sinNada = new EntregableAnexo(e.nombre(), e.detalle(), e.entregado());
            if (!puedeAbrir || !e.entregado()) {
                entregables.add(sinNada);
            } else if (e.archivoId() != null) {
                entregables.add(new EntregableAnexo(e.nombre(), e.detalle(), true, e.archivoId(),
                        firmado(quien, e.archivoId(), informe.postulacionId(), "un entregable").orElse(null)));
            } else if (LoComunDelInforme.esWeb(e.url())) {
                entregables.add(new EntregableAnexo(e.nombre(), e.detalle(), true, null, e.url().strip()));
            } else {
                entregables.add(sinNada);
            }
        }
        return new Anexo(anexo.titulo(), anexo.subtitulo(), anexo.caso(), anexo.preguntas(),
                anexo.entregablesResumen(), entregables);
    }

    /** El enlace firmado de un archivo, o vacío si no se pudo firmar o no es web. */
    private Optional<String> firmado(ContextoUsuario quien, Long archivoId, Long postulacionId, String deQue) {
        Optional<EnlaceArchivo> firmado;
        try {
            firmado = archivos.enlaceDeVolcado(quien, archivoId);
        } catch (RuntimeException noSePudo) {
            log.warn("No se pudo firmar el enlace de {} de la postulación {}: {}", deQue, postulacionId,
                    noSePudo.toString());
            return Optional.empty();
        }
        return firmado.map(EnlaceArchivo::url).filter(LoComunDelInforme::esWeb);
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
