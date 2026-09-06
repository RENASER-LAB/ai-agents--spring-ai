package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.perfil.entity.CertificacionPerfil;
import com.renaser.ai.ai_engine.perfil.entity.LecturaCvPerfil;
import com.renaser.ai.ai_engine.perfil.entity.PerfilCandidato;
import com.renaser.ai.ai_engine.perfil.repository.CertificacionPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.LecturaCvPerfilRepository;
import com.renaser.ai.ai_engine.perfil.repository.PerfilCandidatoRepository;
import com.renaser.ai.ai_engine.perfil.service.PortadasDeLaCasa;
import com.renaser.ai.ai_engine.perfil.service.ServicioArchivosDelPerfil;
import com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Ver {@link ServicioArchivosDelPerfil}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioArchivosDelPerfilImpl implements ServicioArchivosDelPerfil {

    private final PerfilCandidatoRepository perfiles;
    private final CertificacionPerfilRepository certificaciones;
    private final LecturaCvPerfilRepository lecturas;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ColaCalificacionIa cola;
    // El otro recibo de «este archivo ya se leyó»: el de las postulaciones. Ver yaSeLeyo.
    private final DatoCvRepository datosCv;
    // Y quien sabe si lo que aquel recibo propuso sigue en el perfil.
    private final ServicioPropuestaPerfil propuesta;

    // ==================== La foto ====================

    @Override
    @Transactional
    public void guardarFoto(ContextoUsuario quien, MultipartFile archivo) {
        PerfilCandidato perfil = elDe(quien);
        Archivo nuevo = almacen.guardarImagen(quien.organizacionId(), archivo);
        soltar(quien, perfil.getFotoArchivoId());
        perfil.setFotoArchivoId(nuevo.getId());
        tocar(perfil);
    }

    @Override
    @Transactional
    public void quitarFoto(ContextoUsuario quien) {
        PerfilCandidato perfil = elDe(quien);
        soltar(quien, perfil.getFotoArchivoId());
        perfil.setFotoArchivoId(null);
        tocar(perfil);
    }

    @Override
    public Contenido foto(ContextoUsuario quien) {
        return contenidoDe(quien, elDe(quien).getFotoArchivoId(), "foto");
    }

    // ==================== La portada ====================

    @Override
    @Transactional
    public void guardarPortada(ContextoUsuario quien, MultipartFile archivo) {
        PerfilCandidato perfil = elDe(quien);
        Archivo nuevo = almacen.guardarImagen(quien.organizacionId(), archivo);
        soltar(quien, perfil.getPortadaArchivoId());
        perfil.setPortadaArchivoId(nuevo.getId());
        // Excluyentes: lo impone un CHECK en la base, y aquí se respeta antes de llegar a él
        // para no contestar un 500 donde la respuesta correcta es «ahora tienes la tuya».
        perfil.setPortadaGaleria(null);
        tocar(perfil);
    }

    @Override
    @Transactional
    public void elegirPortadaDeGaleria(ContextoUsuario quien, String codigo) {
        PortadasDeLaCasa.exigirValido(codigo);
        PerfilCandidato perfil = elDe(quien);
        soltar(quien, perfil.getPortadaArchivoId());
        perfil.setPortadaArchivoId(null);
        perfil.setPortadaGaleria(codigo);
        tocar(perfil);
    }

    @Override
    @Transactional
    public void quitarPortada(ContextoUsuario quien) {
        PerfilCandidato perfil = elDe(quien);
        soltar(quien, perfil.getPortadaArchivoId());
        perfil.setPortadaArchivoId(null);
        perfil.setPortadaGaleria(null);
        tocar(perfil);
    }

    @Override
    public Contenido portada(ContextoUsuario quien) {
        return contenidoDe(quien, elDe(quien).getPortadaArchivoId(), "portada");
    }

    // ==================== El currículum ====================

    /**
     * Sube su currículum y arranca la lectura.
     *
     * <p>⚠️ <b>Dos cosas que no se ven en el código y sí en la factura.</b> La primera: solo
     * se conserva el último (RF-162), así que el anterior se suelta. La segunda: si la huella
     * del archivo coincide con una lectura que esta persona ya pagó, <b>no se llama al
     * modelo</b> (RF-161) — su perfil ya tiene lo que ese PDF decía, y volver a leerlo
     * costaría dinero para proponer exactamente lo mismo.
     */
    @Override
    @Transactional
    public void guardarCurriculum(ContextoUsuario quien, MultipartFile archivo) {
        PerfilCandidato perfil = elDe(quien);
        Archivo nuevo = almacen.guardar(quien.organizacionId(), archivo);

        soltar(quien, perfil.getCvArchivoId());
        perfil.setCvArchivoId(nuevo.getId());
        perfil.setCvActualizadoEn(Instant.now());
        tocar(perfil);

        // ⚠️ saveAndFlush y no save: el índice parcial de «una lectura viva por persona»
        // revienta si el INSERT de la nueva llega antes de que el UPDATE cierre la vieja, y
        // Hibernate ordena los INSERT primero dentro de la misma transacción.
        lecturas.findByPersonaIdAndEstado(quien.personaId(), LecturaCvPerfil.EN_CURSO)
                .ifPresent(vieja -> {
                    vieja.setEstado(LecturaCvPerfil.NO_LEGIBLE);
                    vieja.setMotivo("Se subió otro currículum antes de terminar de leer este");
                    vieja.setTerminadoEn(Instant.now());
                    lecturas.saveAndFlush(vieja);
                });

        if (yaSeLeyo(quien.personaId(), nuevo)) {
            lecturas.save(LecturaCvPerfil.builder()
                    .personaId(quien.personaId()).archivoId(nuevo.getId())
                    .estado(LecturaCvPerfil.LISTA).intentos(0)
                    .motivo("Este mismo archivo ya se había leído: no se vuelve a pagar")
                    .creadoEn(Instant.now()).terminadoEn(Instant.now())
                    .build());
            log.info("El currículum del perfil de la persona {} ya se había leído: se reutiliza",
                    quien.personaId());
            return;
        }

        LecturaCvPerfil lectura = lecturas.save(LecturaCvPerfil.builder()
                .personaId(quien.personaId()).archivoId(nuevo.getId())
                .estado(LecturaCvPerfil.EN_CURSO).intentos(0)
                .creadoEn(Instant.now())
                .build());

        // Si la cola está apagada —o la organización sin cupo— no se encola, y entonces la
        // lectura se queda EN_CURSO para siempre. Se cierra aquí mismo: la pantalla sondea
        // ese estado cada cinco segundos y un «estamos leyendo tu currículum» eterno es peor
        // que decir que no se pudo.
        if (!cola.encolarDatosCvDelPerfil(quien.organizacionId(), lectura.getId())) {
            lectura.setEstado(LecturaCvPerfil.NO_LEGIBLE);
            lectura.setMotivo("La lectura automática no está disponible en este momento");
            lectura.setTerminadoEn(Instant.now());
            lecturas.save(lectura);
        }
    }

    /**
     * Quita su currículum del perfil.
     *
     * <p>⚠️ <b>Las lecturas NO se borran aquí, y eso es lo que evita cobrar dos veces.</b>
     * Cada fila {@code LISTA} es el recibo de un archivo ya leído; borrarlas dejaba que
     * quitar y volver a subir el mismo PDF llamara otra vez al modelo para proponer
     * exactamente los mismos datos, que es justo lo que prohíbe el RF-161. La pantalla no se
     * confunde porque el estado se busca por el archivo que hay ahora, no por la lectura más
     * reciente. Solo desaparecen cuando desaparece el perfil entero, en
     * {@code ServicioCicloVidaPerfil}.
     *
     * <p>Lo que la persona ya llevó a su perfil se queda: es suyo y ya lo revisó.
     */
    @Override
    @Transactional
    public void quitarCurriculum(ContextoUsuario quien) {
        PerfilCandidato perfil = elDe(quien);
        soltar(quien, perfil.getCvArchivoId());
        perfil.setCvArchivoId(null);
        perfil.setCvActualizadoEn(null);
        tocar(perfil);

        // Si lo quitó mientras se leía, esa lectura ya no puede terminar: su archivo acaba
        // de dejar de estar guardado. Se cierra aquí en vez de esperar a que el agente
        // tropiece con el hueco y gaste los tres reintentos en descubrirlo.
        lecturas.findByPersonaIdAndEstado(quien.personaId(), LecturaCvPerfil.EN_CURSO)
                .ifPresent(viva -> {
                    viva.setEstado(LecturaCvPerfil.NO_LEGIBLE);
                    viva.setMotivo("Se quitó el currículum antes de terminar de leerlo");
                    viva.setTerminadoEn(Instant.now());
                    lecturas.save(viva);
                });
    }

    @Override
    public Contenido curriculum(ContextoUsuario quien) {
        return contenidoDe(quien, elDe(quien).getCvArchivoId(), "currículum");
    }

    // ==================== Los diplomas ====================

    @Override
    @Transactional
    public void guardarDiploma(ContextoUsuario quien, Long certificacionId, MultipartFile archivo) {
        CertificacionPerfil fila = miCertificacion(quien, certificacionId);
        // Un diploma es un PDF o una foto del papel: valen los dos, así que se prueban en
        // ese orden y solo se rechaza si no es ninguno.
        Archivo nuevo = esImagen(archivo)
                ? almacen.guardarImagen(quien.organizacionId(), archivo)
                : almacen.guardar(quien.organizacionId(), archivo);
        soltar(quien, fila.getArchivoId());
        fila.setArchivoId(nuevo.getId());
        certificaciones.save(fila);
    }

    @Override
    @Transactional
    public void quitarDiploma(ContextoUsuario quien, Long certificacionId) {
        CertificacionPerfil fila = miCertificacion(quien, certificacionId);
        soltar(quien, fila.getArchivoId());
        fila.setArchivoId(null);
        certificaciones.save(fila);
    }

    @Override
    public Contenido diploma(ContextoUsuario quien, Long certificacionId) {
        return contenidoDe(quien, miCertificacion(quien, certificacionId).getArchivoId(),
                "diploma");
    }

    // ==================== Lo común ====================

    /** El perfil del que llama; se crea perezosamente, igual que en el resto del portal. */
    private PerfilCandidato elDe(ContextoUsuario quien) {
        return perfiles.findByPersonaId(quien.personaId())
                .orElseGet(() -> perfiles.save(PerfilCandidato.builder()
                        .personaId(quien.personaId())
                        .creadoEn(Instant.now()).actualizadoEn(Instant.now())
                        .build()));
    }

    /** Lo ajeno responde 404, no 403: decir «prohibido» ya confirmaría que existe. */
    private CertificacionPerfil miCertificacion(ContextoUsuario quien, Long id) {
        Long perfilId = perfiles.findByPersonaId(quien.personaId())
                .map(PerfilCandidato::getId).orElse(null);
        return certificaciones.findById(id)
                .filter(c -> c.getPerfilCandidatoId().equals(perfilId))
                .orElseThrow(() -> new ResourceNotFoundException("Certificación", "id", id));
    }

    /**
     * Los bytes de uno de sus archivos.
     *
     * <p>⚠️ <b>Se pide con la organización, y no es ceremonia.</b> El id ya viene de su
     * propia fila de perfil, así que la persona correcta es segura por construcción; la
     * organización es el segundo cerrojo que exige la regla de arquitectura, y aquí encaja
     * de verdad porque estos archivos se sellaron con esta misma organización al subirlos.
     */
    private Contenido contenidoDe(ContextoUsuario quien, Long archivoId, String queEs) {
        if (archivoId == null) {
            throw new ResourceNotFoundException("Archivo", "de tu " + queEs, archivoId);
        }
        Archivo archivo = archivos.findByIdAndOrganizacionId(archivoId, quien.organizacionId())
                .filter(a -> a.getBorradoEn() == null && a.getRuta() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", archivoId));
        return new Contenido(almacen.leer(archivo), archivo.getNombreOriginal(),
                archivo.getTipo());
    }

    /**
     * Suelta el archivo anterior: se borra su contenido y la fila se queda.
     *
     * <p>La fila se conserva a propósito —es lo que hace el resto del sistema— porque saber
     * que existió sin poder recuperarlo es lo que permite explicar un hueco más tarde.
     */
    private void soltar(ContextoUsuario quien, Long archivoId) {
        if (archivoId == null) {
            return;
        }
        archivos.findByIdAndOrganizacionId(archivoId, quien.organizacionId())
                .filter(a -> a.getBorradoEn() == null)
                .ifPresent(almacen::borrarContenido);
    }

    /**
     * Si esta persona ya pagó la lectura de un archivo con este mismo contenido.
     *
     * <p>⚠️ <b>Se miran los DOS recibos, no solo el del perfil.</b> Un currículum se lee
     * desde aquí o al postular, y el de postular escribe {@code dato_cv} — pero propone lo
     * mismo a este perfil en la misma transacción ({@code PuenteCalificacionIa.guardarDatos}).
     * Quien postuló con su PDF y luego lo guarda en su perfil ya tiene ahí lo que ese PDF
     * decía: volver a leerlo era pagar dos veces por el mismo resultado (RF-161).
     *
     * <p>⚠️ <b>Pero el recibo de la postulación solo vale mientras aquellas propuestas sigan
     * en el perfil.</b> {@code dato_cv} guarda la ficha de la postulación, no lo que se
     * propuso aquí, y el barrido de retención se lleva el perfil entero: sin esa condición,
     * quien volviera después de que le barrieran el perfil vería un «ya está: revisa lo que
     * encontramos» sobre un perfil vacío. Sin propuestas vivas se vuelve a leer — se paga
     * una vez, y entonces sí hay algo que revisar.
     */
    private boolean yaSeLeyo(Long personaId, Archivo nuevo) {
        if (nuevo.getContenidoHash() == null) {
            return false;
        }
        List<Long> mismos = archivos.findByContenidoHash(nuevo.getContenidoHash()).stream()
                .map(Archivo::getId)
                .toList();
        if (!lecturas.findByPersonaIdAndArchivoIdInAndEstado(
                personaId, mismos, LecturaCvPerfil.LISTA).isEmpty()) {
            return true;
        }
        return !datosCv.fichasDeLaPersonaConHash(personaId, nuevo.getContenidoHash()).isEmpty()
                && propuesta.conservaLoPropuestoDeUnCurriculum(personaId);
    }

    private static boolean esImagen(MultipartFile archivo) {
        String tipo = Optional.ofNullable(archivo.getContentType()).orElse("");
        return tipo.startsWith("image/");
    }

    private void tocar(PerfilCandidato perfil) {
        perfil.setActualizadoEn(Instant.now());
        perfiles.save(perfil);
    }
}
