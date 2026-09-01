package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.ServicioPlantillaPrueba;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.MAXIMO_GUIA_CALIFICACION;

@Service
@RequiredArgsConstructor
public class ServicioPlantillaPruebaImpl implements ServicioPlantillaPrueba {

    private static final BigDecimal TOLERANCIA = BigDecimal.valueOf(0.01);
    private static final int UNIVERSALES_MIN = 8;
    private static final int UNIVERSALES_MAX = 10;
    private static final int ESPECIFICAS_MIN = 3;
    private static final int ESPECIFICAS_MAX = 5;
    /**
     * El suelo del reloj de una prueba, en minutos.
     *
     * <p><b>Ya no hay techo, ni un mínimo de una hora.</b> El rango 60-120 se retiró: quien
     * escribe la prueba sabe cuánto dura, y una de veinte minutos o de cuatro horas es una
     * decisión suya, no un error.
     *
     * <p>⚠️ <b>Pero el suelo hace falta más que antes.</b> Desde que los minutos de la
     * vacante convierten cualquier prueba en cronometrada, un uno aquí sería una prueba que
     * {@code entregarVencidos} entrega sola sesenta segundos después de abrirla, con la
     * pantalla todavía cargando. Cinco es el mismo suelo que valida la ficha de la vacante.
     */
    private static final int DURACION_MINIMA = 5;

    private final PlantillaPruebaRepository plantillas;
    private final VersionPlantillaPruebaRepository versiones;
    private final VarianteCambioRepository variantes;
    private final PreguntaPruebaRepository preguntasCatalogo;
    private final PreguntaVersionPlantillaRepository preguntasElegidas;
    private final EntregableRequeridoRepository entregablesRequeridos;
    private final CriterioRepository criterios;
    private final ServicioAuditoria auditoria;
    private final DuenoDelInstrumento dueno;
    private final AlmacenArchivos almacen;

    @Override
    @Transactional
    public Long crearPlantilla(ContextoUsuario quien, CrearPlantilla datos) {
        PlantillaPrueba plantilla = plantillas.save(PlantillaPrueba.builder()
                .organizacionId(quien.organizacionId())
                .puestoId(datos.puestoId())
                .nombre(datos.nombre())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_plantilla_prueba",
                "plantilla_prueba", plantilla.getId(), null, Map.of("nombre", datos.nombre()), null);
        return plantilla.getId();
    }

    @Override
    public List<PlantillaResponse> listarPlantillas(ContextoUsuario quien) {
        // El resolutor decide de quién son las pruebas que esta organización ve: las
        // suyas si personalizó, las de la plataforma si no.
        return plantillas.findByOrganizacionIdOrderByCreadoEnDesc(
                        dueno.duenoDe(quien.organizacionId(), Instrumento.PRUEBA)).stream()
                .map(p -> new PlantillaResponse(p.getId(), p.getNombre(), p.getPuestoId(), p.isEsActiva()))
                .toList();
    }

    @Override
    @Transactional
    public Long crearVersion(ContextoUsuario quien, Long plantillaId, CrearVersion datos) {
        PlantillaPrueba plantilla = laPlantillaPropia(quien, plantillaId);
        exigirTiemposCoherentes(datos);
        exigirGuiaRazonable(datos);
        Integer siguiente = versiones.findByPlantillaPruebaIdOrderByVersionDesc(plantillaId).stream()
                .findFirst().map(v -> v.getVersion() + 1).orElse(1);

        VersionPlantillaPrueba version = versiones.save(VersionPlantillaPrueba.builder()
                .plantillaPruebaId(plantilla.getId())
                .version(siguiente)
                .enunciado(datos.enunciado())
                .materiales(datos.materiales())
                .herramientasPermitidas(datos.herramientasPermitidas())
                .modalidad(datos.modalidad())
                .duracionMinutos(datos.duracionMinutos())
                .plazoDias(datos.plazoDias())
                .minutoCambioMin(datos.minutoCambioMin())
                .minutoCambioMax(datos.minutoCambioMax())
                .minutosExtra(datos.minutosExtra())
                .guiaCalificacion(datos.guiaCalificacion())
                .estado("BORRADOR")
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_version_plantilla_prueba",
                "version_plantilla_prueba", version.getId(), null,
                Map.of("plantillaId", plantillaId), null);
        return version.getId();
    }

    @Override
    @Transactional
    public void publicarVersion(ContextoUsuario quien, Long versionId) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);

        // El nulo se comprueba aquí como segunda línea, no como la primera: la V15 ya trae
        // `CHECK (modalidad != 'CRONOMETRADA' OR duracion_minutos IS NOT NULL)`, así que por
        // la API no se puede ni crear. Se deja escrito porque `iniciar` usa este número sin
        // red —el NPE saldría en la cara del candidato— y porque el rango que se acaba de
        // quitar era lo único que lo decía en Java.
        if ("CRONOMETRADA".equals(version.getModalidad())) {
            Integer duracion = version.getDuracionMinutos();
            if (duracion == null || duracion < DURACION_MINIMA) {
                throw new IllegalArgumentException(
                        "Una prueba cronometrada dura al menos %d minutos; esta pide %s"
                                .formatted(DURACION_MINIMA,
                                        duracion == null ? "no decirlo" : duracion));
            }
        }

        validarCuotaPreguntas(versionId);
        validarRubrica(versionId);

        version.setEstado("PUBLICADA");
        version.setPublicadaPorUsuarioId(quien.usuarioId());
        version.setPublicadaEn(Instant.now());
        versiones.save(version);
        auditoria.registrar(quien.organizacionId(), quien, "publicar_version_plantilla_prueba",
                "version_plantilla_prueba", versionId,
                Map.of("estado", "BORRADOR"), Map.of("estado", "PUBLICADA"), null);
    }

    @Override
    public List<VersionResponse> listarVersiones(ContextoUsuario quien, Long plantillaId) {
        // Misma puerta que verVersion: se ven las propias y, si esta organización no
        // personalizó, las de la plataforma. Un id ajeno responde 404 aquí mismo, antes
        // de leer ninguna versión.
        laPlantillaVisible(quien, plantillaId);
        return versiones.findByPlantillaPruebaIdOrderByVersionDesc(plantillaId).stream()
                .map(ServicioPlantillaPruebaImpl::comoRespuesta)
                .toList();
    }

    @Override
    public VersionCompleta verVersion(ContextoUsuario quien, Long versionId) {
        // La versión completa lleva la rúbrica y sus puntos: el examen entero de una
        // convocatoria. Antes se servía por id suelto — cualquier empresa podía leer la
        // prueba de otra. Se deriva a su plantilla y se valida contra el dueño resuelto:
        // con la bandera apagada se VEN las versiones de la plataforma, ninguna más.
        VersionPlantillaPrueba v = laVersionVisible(quien, versionId);

        List<VarianteResponse> vs = variantes.findByVersionPlantillaPruebaIdOrderByOrden(versionId)
                .stream()
                .map(x -> new VarianteResponse(x.getId(), x.getTexto(), x.getOrden())).toList();

        List<Long> idsElegidas = preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId)
                .stream().map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();
        // ⚠️ `findByIdIn` devuelve las filas en el orden que quiera la base: el orden que
        // alguien eligió vive en `pregunta_version_plantilla`, no en el catálogo. Sin volver
        // a ordenarlas aquí, mover una pregunta de sitio no cambiaría nada de lo que se ve.
        Map<Long, Integer> posicionElegida = IntStream.range(0, idsElegidas.size()).boxed()
                .collect(Collectors.toMap(idsElegidas::get, i -> i));
        List<PreguntaPruebaResponse> ps = preguntasCatalogo.findByIdIn(idsElegidas).stream()
                .sorted(Comparator.comparingInt(
                        p -> posicionElegida.getOrDefault(p.getId(), Integer.MAX_VALUE)))
                .map(p -> new PreguntaPruebaResponse(p.getId(), p.getCodigo(), p.getEnunciado(),
                        p.getTipo(), p.getPuestoId()))
                .toList();

        List<EntregableRequeridoResponse> es = entregablesRequeridos
                .findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .map(e -> new EntregableRequeridoResponse(e.getId(), e.getNombre(), e.getDetalle(),
                        e.getFormato(), e.isEsObligatorio()))
                .toList();

        List<CriterioRubricaResponse> rubrica = criterios
                .findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .map(c -> new CriterioRubricaResponse(c.getId(), c.getCodigo(),
                        c.getNombre(), c.getDescripcion(),
                        c.getPuntos() == null ? null : c.getPuntos().doubleValue(),
                        c.getMetodoVerificacion()))
                .toList();

        return new VersionCompleta(comoRespuesta(v), vs, ps, es, rubrica);
    }

    /**
     * La versión, tal como sale por la API.
     *
     * <p>Un solo sitio para las dos puertas que la sirven —el listado y la versión
     * completa—: en cuanto la versión gane un campo más, olvidarse de uno de los dos
     * daría un panel que enseña el dato en una pantalla y no en la otra.
     */
    private static VersionResponse comoRespuesta(VersionPlantillaPrueba v) {
        return new VersionResponse(v.getId(), v.getPlantillaPruebaId(), v.getVersion(),
                v.getEnunciado(), v.getMateriales(), v.getHerramientasPermitidas(),
                v.getModalidad(), v.getDuracionMinutos(), v.getPlazoDias(),
                v.getMinutoCambioMin(), v.getMinutoCambioMax(), v.getMinutosExtra(),
                v.getEstado(), v.getPublicadaEn(),
                v.getGuiaCalificacion(), v.getUrlConsigna());
    }

    @Override
    @Transactional
    public Long agregarVariante(ContextoUsuario quien, Long versionId, CrearVariante datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        int siguiente = siguienteOrden(
                variantes.findByVersionPlantillaPruebaIdOrderByOrden(versionId), VarianteCambio::getOrden);
        VarianteCambio v = variantes.save(VarianteCambio.builder()
                .versionPlantillaPruebaId(version.getId())
                .texto(datos.texto())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return v.getId();
    }

    @Override
    @Transactional
    public Long crearPreguntaCatalogo(ContextoUsuario quien, CrearPreguntaPrueba datos) {
        int siguiente = preguntasCatalogo.findByTipo(datos.tipo()).size() + 1;
        PreguntaPrueba p = preguntasCatalogo.save(PreguntaPrueba.builder()
                .codigo(datos.codigo())
                .enunciado(datos.enunciado())
                .tipo(datos.tipo())
                .puestoId(datos.puestoId())
                .revela(datos.revela())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_pregunta_prueba",
                "pregunta_prueba", p.getId(), null, Map.of("codigo", datos.codigo()), null);
        return p.getId();
    }

    @Override
    public List<PreguntaPruebaResponse> listarPreguntasCatalogo(String tipo) {
        List<PreguntaPrueba> lista = tipo == null ? preguntasCatalogo.findAll()
                : preguntasCatalogo.findByTipo(tipo);
        return lista.stream()
                .map(p -> new PreguntaPruebaResponse(p.getId(), p.getCodigo(), p.getEnunciado(),
                        p.getTipo(), p.getPuestoId()))
                .toList();
    }

    @Override
    @Transactional
    public void elegirPregunta(ContextoUsuario quien, Long versionId, ElegirPregunta datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        preguntasCatalogo.findById(datos.preguntaPruebaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta de prueba", "id", datos.preguntaPruebaId()));
        int siguiente = siguienteOrden(
                preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId),
                PreguntaVersionPlantilla::getOrden);
        preguntasElegidas.save(PreguntaVersionPlantilla.builder()
                .versionPlantillaPruebaId(version.getId())
                .preguntaPruebaId(datos.preguntaPruebaId())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    @Transactional
    public Long agregarEntregableRequerido(ContextoUsuario quien, Long versionId, CrearEntregableRequerido datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        int siguiente = siguienteOrden(
                entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(versionId),
                EntregableRequerido::getOrden);
        EntregableRequerido e = entregablesRequeridos.save(EntregableRequerido.builder()
                .versionPlantillaPruebaId(version.getId())
                .nombre(datos.nombre())
                .detalle(datos.detalle())
                .formato(datos.formato())
                .esObligatorio(datos.esObligatorio())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return e.getId();
    }

    @Override
    @Transactional
    public Long agregarCriterioRubrica(ContextoUsuario quien, Long versionId, CrearCriterioRubrica datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        exigirCodigoLibreEnLaRubrica(versionId, datos.codigo(), null);
        int siguiente = siguienteOrden(
                criterios.findByVersionPlantillaPruebaIdOrderByOrden(versionId), Criterio::getOrden);
        Criterio c = criterios.save(Criterio.builder()
                .codigo(datos.codigo())
                .nombre(datos.nombre())
                .descripcion(datos.descripcion())
                .etapaCodigo("PRUEBA_PUESTO")
                .versionPlantillaPruebaId(version.getId())
                .puntos(BigDecimal.valueOf(datos.puntos()))
                .metodoVerificacion(datos.metodoVerificacion())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return c.getId();
    }

    // ============ Corregir y quitar, solo en borrador ============
    //
    // Todo lo de aquí abajo entra por `laVersionEnBorrador`: una versión PUBLICADA responde
    // 409 y no se toca. Publicar sigue siendo el punto de no retorno (RF-90); lo que cambia
    // es que antes de publicar ya no hay operaciones irreversibles.
    //
    // Estas sí se auditan una por una, aunque los `agregar*` de arriba no lo hagan. No es
    // una inconsistencia: lo que se añade se ve en la versión y se puede quitar; lo que se
    // quita no deja rastro en ninguna parte, así que si no se anota aquí no se anota nunca.

    @Override
    @Transactional
    public void actualizarVersion(ContextoUsuario quien, Long versionId, CrearVersion datos) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);
        exigirTiemposCoherentes(datos);
        exigirGuiaRazonable(datos);

        // ⚠️ `Map.of` no admite valores nulos, y casi todo esto puede serlo. De ahí los
        // `String.valueOf`, que también es por lo que la guía viaja aquí como su longitud y
        // no como su texto: ver `largoDe`.
        Map<String, Object> antes = Map.of(
                "enunciado", version.getEnunciado(),
                "modalidad", version.getModalidad(),
                "duracionMinutos", String.valueOf(version.getDuracionMinutos()),
                "plazoDias", String.valueOf(version.getPlazoDias()),
                "largoGuiaCalificacion", largoDe(version.getGuiaCalificacion()));

        version.setEnunciado(datos.enunciado());
        version.setMateriales(datos.materiales());
        version.setHerramientasPermitidas(datos.herramientasPermitidas());
        version.setModalidad(datos.modalidad());
        version.setDuracionMinutos(datos.duracionMinutos());
        version.setPlazoDias(datos.plazoDias());
        version.setMinutoCambioMin(datos.minutoCambioMin());
        version.setMinutoCambioMax(datos.minutoCambioMax());
        version.setMinutosExtra(datos.minutosExtra());
        // La guía SÍ se reemplaza, incluida a nulo: mandarla vacía es como se quita una guía
        // que estaba de más, y es lo mismo que hace `materiales` justo aquí arriba.
        version.setGuiaCalificacion(datos.guiaCalificacion());
        // Lo que este método NO toca, dicho para que nadie lo añada por simetría: el número
        // de versión, el estado y las tres columnas de la publicación son historia, no datos
        // del examen; `vacanteId` y `urlConsigna` no viajan en este contrato y ponerlos a
        // nulo aquí borraría en silencio el enunciado subido — el enunciado se cambia por su
        // propio endpoint, `subirConsigna`.
        versiones.save(version);

        auditoria.registrar(quien.organizacionId(), quien, "editar_version_plantilla_prueba",
                "version_plantilla_prueba", versionId, antes,
                Map.of("enunciado", datos.enunciado(),
                        "modalidad", datos.modalidad(),
                        "duracionMinutos", String.valueOf(datos.duracionMinutos()),
                        "plazoDias", String.valueOf(datos.plazoDias()),
                        "largoGuiaCalificacion", largoDe(datos.guiaCalificacion())), null);
    }

    @Override
    @Transactional
    public ConsignaResponse subirConsigna(ContextoUsuario quien, Long versionId,
                                          MultipartFile archivo) {
        VersionPlantillaPrueba version = laVersionEnBorrador(quien, versionId);

        // Que sea PDF o Word lo comprueba el almacén (`TiposDeArchivo.exigirValido`), que es
        // el único embudo por el que pasan los bytes. No se repite aquí: dos listas de
        // formatos permitidos acaban siendo dos listas distintas.
        Archivo guardado = almacen.guardar(quien.organizacionId(), archivo);

        // El enlace se firma para meses y no para minutos, porque no se le enseña a un
        // navegador: se guarda, y lo pega el correo PRUEBA_DISPONIBLE cuando al candidato le
        // toca la prueba. El porqué entero está en `AlmacenArchivos.urlDeConsigna`.
        AlmacenArchivos.EnlaceFirmado enlace = almacen.urlDeConsigna(guardado)
                .orElseThrow(() -> new IllegalStateException(
                        "El almacén de archivos de este entorno no reparte enlaces, así que "
                                + "no puede servir el enunciado de una prueba"));

        String urlAnterior = version.getUrlConsigna();
        version.setUrlConsigna(enlace.url());
        versiones.save(version);

        auditoria.registrar(quien.organizacionId(), quien, "subir_consigna_de_version_prueba",
                "version_plantilla_prueba", versionId,
                Map.of("urlConsigna", String.valueOf(urlAnterior)),
                Map.of("urlConsigna", enlace.url(),
                        "archivoId", guardado.getId(),
                        "nombreOriginal", String.valueOf(guardado.getNombreOriginal())), null);

        return new ConsignaResponse(guardado.getId(), enlace.url(), enlace.expira());
    }

    @Override
    @Transactional
    public void quitarPregunta(ContextoUsuario quien, Long versionId, Long preguntaPruebaId) {
        laVersionEnBorrador(quien, versionId);
        PreguntaVersionPlantilla elegida = preguntasElegidas
                .findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .filter(p -> p.getPreguntaPruebaId().equals(preguntaPruebaId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pregunta elegida en esta versión", "id", preguntaPruebaId));

        // ⚠️ Se borra la ELECCIÓN, no la pregunta. `pregunta_prueba` es un catálogo global
        // que varias versiones eligen a la vez: borrarla de ahí vaciaría el examen de otras
        // plantillas, y las respuestas ya escritas la apuntan por clave foránea. Lo único
        // que desaparece aquí es la fila que une esta versión con esa pregunta.
        preguntasElegidas.delete(elegida);

        auditoria.registrar(quien.organizacionId(), quien, "quitar_pregunta_de_version_prueba",
                "pregunta_version_plantilla", versionId,
                Map.of("preguntaPruebaId", preguntaPruebaId, "orden", elegida.getOrden()),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarEntregableRequerido(ContextoUsuario quien, Long entregableId,
                                              CrearEntregableRequerido datos) {
        EntregableRequerido e = elEntregableEditable(quien, entregableId);
        Map<String, Object> antes = Map.of("nombre", e.getNombre(), "detalle", e.getDetalle(),
                "formato", e.getFormato(), "esObligatorio", e.isEsObligatorio());
        e.setNombre(datos.nombre());
        e.setDetalle(datos.detalle());
        e.setFormato(datos.formato());
        e.setEsObligatorio(datos.esObligatorio());
        entregablesRequeridos.save(e);
        auditoria.registrar(quien.organizacionId(), quien, "editar_entregable_requerido",
                "entregable_requerido", entregableId, antes,
                Map.of("nombre", datos.nombre(), "detalle", datos.detalle(),
                        "formato", datos.formato(), "esObligatorio", datos.esObligatorio()), null);
    }

    @Override
    @Transactional
    public void quitarEntregableRequerido(ContextoUsuario quien, Long entregableId) {
        EntregableRequerido e = elEntregableEditable(quien, entregableId);
        entregablesRequeridos.delete(e);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_entregable_requerido",
                "entregable_requerido", entregableId,
                Map.of("nombre", e.getNombre(), "formato", e.getFormato(), "orden", e.getOrden()),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarCriterioRubrica(ContextoUsuario quien, Long criterioId,
                                          CrearCriterioRubrica datos) {
        Criterio c = elCriterioDeLaRubricaEditable(quien, criterioId);
        exigirCodigoLibreEnLaRubrica(c.getVersionPlantillaPruebaId(), datos.codigo(), criterioId);
        Map<String, Object> antes = Map.of("codigo", c.getCodigo(), "nombre", c.getNombre(),
                "puntos", String.valueOf(c.getPuntos()),
                "metodoVerificacion", c.getMetodoVerificacion());
        c.setCodigo(datos.codigo());
        c.setNombre(datos.nombre());
        c.setDescripcion(datos.descripcion());
        c.setPuntos(BigDecimal.valueOf(datos.puntos()));
        c.setMetodoVerificacion(datos.metodoVerificacion());
        // La etapa y la versión no se tocan: son lo que hace de este criterio uno de la
        // rúbrica de esta prueba y no uno global. Cambiarlos lo mudaría de dueño.
        criterios.save(c);
        auditoria.registrar(quien.organizacionId(), quien, "editar_criterio_rubrica_prueba",
                "criterio", criterioId, antes,
                Map.of("codigo", datos.codigo(), "nombre", datos.nombre(),
                        "puntos", String.valueOf(datos.puntos()),
                        "metodoVerificacion", datos.metodoVerificacion()), null);
    }

    @Override
    @Transactional
    public void quitarCriterioRubrica(ContextoUsuario quien, Long criterioId) {
        Criterio c = elCriterioDeLaRubricaEditable(quien, criterioId);
        // Sin notas que lo apunten: `nota_criterio` solo nace de un intento, y un intento
        // solo nace de una versión PUBLICADA (`asignarPlantillaPrueba` no acepta otras).
        // Por eso quitar un criterio de un borrador no puede chocar con esa clave foránea.
        criterios.delete(c);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_criterio_rubrica_prueba",
                "criterio", criterioId,
                Map.of("codigo", c.getCodigo(), "nombre", c.getNombre(),
                        "puntos", String.valueOf(c.getPuntos())),
                null, null);
    }

    @Override
    @Transactional
    public void actualizarVariante(ContextoUsuario quien, Long varianteId, CrearVariante datos) {
        VarianteCambio v = laVarianteEditable(quien, varianteId);
        Map<String, Object> antes = Map.of("texto", v.getTexto());
        v.setTexto(datos.texto());
        variantes.save(v);
        auditoria.registrar(quien.organizacionId(), quien, "editar_variante_cambio",
                "variante_cambio", varianteId, antes, Map.of("texto", datos.texto()), null);
    }

    @Override
    @Transactional
    public void quitarVariante(ContextoUsuario quien, Long varianteId) {
        VarianteCambio v = laVarianteEditable(quien, varianteId);
        variantes.delete(v);
        auditoria.registrar(quien.organizacionId(), quien, "eliminar_variante_cambio",
                "variante_cambio", varianteId,
                Map.of("texto", v.getTexto(), "orden", v.getOrden()), null, null);
    }

    @Override
    @Transactional
    public void reordenarPreguntas(ContextoUsuario quien, Long versionId, ReordenarElementos datos) {
        laVersionEnBorrador(quien, versionId);
        List<PreguntaVersionPlantilla> actuales =
                preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId);
        renumerar(elOrdenPedido(datos.idsEnOrden(), actuales,
                        PreguntaVersionPlantilla::getPreguntaPruebaId, "las preguntas elegidas"),
                maximoOrden(actuales, PreguntaVersionPlantilla::getOrden),
                PreguntaVersionPlantilla::setOrden, preguntasElegidas);
        auditar(quien, "reordenar_preguntas_de_version_prueba", "version_plantilla_prueba",
                versionId, datos);
    }

    @Override
    @Transactional
    public void reordenarEntregables(ContextoUsuario quien, Long versionId, ReordenarElementos datos) {
        laVersionEnBorrador(quien, versionId);
        List<EntregableRequerido> actuales =
                entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(versionId);
        renumerar(elOrdenPedido(datos.idsEnOrden(), actuales,
                        EntregableRequerido::getId, "los entregables"),
                maximoOrden(actuales, EntregableRequerido::getOrden),
                EntregableRequerido::setOrden, entregablesRequeridos);
        auditar(quien, "reordenar_entregables_de_version_prueba", "version_plantilla_prueba",
                versionId, datos);
    }

    @Override
    @Transactional
    public void reordenarVariantes(ContextoUsuario quien, Long versionId, ReordenarElementos datos) {
        laVersionEnBorrador(quien, versionId);
        List<VarianteCambio> actuales = variantes.findByVersionPlantillaPruebaIdOrderByOrden(versionId);
        renumerar(elOrdenPedido(datos.idsEnOrden(), actuales, VarianteCambio::getId, "las variantes"),
                maximoOrden(actuales, VarianteCambio::getOrden),
                VarianteCambio::setOrden, variantes);
        auditar(quien, "reordenar_variantes_de_version_prueba", "version_plantilla_prueba",
                versionId, datos);
    }

    @Override
    @Transactional
    public void reordenarRubrica(ContextoUsuario quien, Long versionId, ReordenarElementos datos) {
        laVersionEnBorrador(quien, versionId);
        List<Criterio> actuales = criterios.findByVersionPlantillaPruebaIdOrderByOrden(versionId);
        renumerar(elOrdenPedido(datos.idsEnOrden(), actuales, Criterio::getId, "los criterios"),
                maximoOrden(actuales, Criterio::getOrden), Criterio::setOrden, criterios);
        auditar(quien, "reordenar_rubrica_de_version_prueba", "version_plantilla_prueba",
                versionId, datos);
    }

    // ============ Apoyo ============

    // Leer resuelve, editar no: con la bandera apagada la organización VE las pruebas de
    // la plataforma —eso contesta el resolutor— pero solo edita las suyas.

    private PlantillaPrueba laPlantillaVisible(ContextoUsuario quien, Long id) {
        return plantillas.findByIdAndOrganizacionId(id, quien.organizacionId())
                .or(() -> plantillas.findByIdAndOrganizacionId(
                        id, dueno.duenoDe(quien.organizacionId(), Instrumento.PRUEBA)))
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de prueba", "id", id));
    }

    private PlantillaPrueba laPlantillaPropia(ContextoUsuario quien, Long id) {
        return plantillas.findByIdAndOrganizacionId(id, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de prueba", "id", id));
    }

    /** Derivar al padre: la versión no sabe de organizaciones, su plantilla sí. */
    private VersionPlantillaPrueba laVersionVisible(ContextoUsuario quien, Long id) {
        VersionPlantillaPrueba version = versiones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Versión de prueba", "id", id));
        laPlantillaVisible(quien, version.getPlantillaPruebaId());
        return version;
    }

    private VersionPlantillaPrueba laVersionEnBorrador(ContextoUsuario quien, Long id) {
        VersionPlantillaPrueba version = versiones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Versión de prueba", "id", id));
        laPlantillaPropia(quien, version.getPlantillaPruebaId());
        if (!"BORRADOR".equals(version.getEstado())) {
            throw new IllegalStateException("Solo se edita una versión en borrador; esta está " + version.getEstado());
        }
        return version;
    }

    private EntregableRequerido elEntregableEditable(ContextoUsuario quien, Long id) {
        EntregableRequerido e = entregablesRequeridos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entregable requerido", "id", id));
        laVersionEnBorrador(quien, e.getVersionPlantillaPruebaId());
        return e;
    }

    private VarianteCambio laVarianteEditable(ContextoUsuario quien, Long id) {
        VarianteCambio v = variantes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variante de cambio", "id", id));
        laVersionEnBorrador(quien, v.getVersionPlantillaPruebaId());
        return v;
    }

    /**
     * Un criterio que de verdad pertenece a la rúbrica de una prueba.
     *
     * <p>⚠️ La tabla {@code criterio} la comparten las tres etapas: los del currículum, los
     * diez de la simulación y las nueve métricas de la validación son <b>globales</b> —valen
     * para todas las vacantes— y se reconocen porque no tienen versión de prueba. Sin esta
     * comprobación, «quitar el criterio 3 de mi rúbrica» y «borrar Integridad del currículum
     * de toda la plataforma» serían la misma llamada, con el mismo permiso.
     *
     * <p>Responde 404 y no 400: por esta puerta ese criterio no existe, igual que no existe
     * el de otra empresa.
     */
    private Criterio elCriterioDeLaRubricaEditable(ContextoUsuario quien, Long id) {
        Criterio c = criterios.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Criterio de rúbrica", "id", id));
        if (c.getVersionPlantillaPruebaId() == null) {
            throw new ResourceNotFoundException("Criterio de rúbrica", "id", id);
        }
        laVersionEnBorrador(quien, c.getVersionPlantillaPruebaId());
        return c;
    }

    /**
     * El número de orden de la fila que viene: {@code max(orden) + 1}, no {@code size() + 1}.
     *
     * <p>⚠️ Mientras no se podía borrar, los dos daban lo mismo. En cuanto se puede, no:
     * quita el entregable número 2 de tres y {@code size()+1} vuelve a proponer el 3, que
     * sigue ocupado — y {@code variante_cambio} y {@code entregable_requerido} llevan un
     * UNIQUE (versión, orden) en la V15. La fila nueva reventaría contra la base.
     *
     * <p><b>El hueco que deja un borrado se queda ahí, a propósito.</b> {@code orden} es una
     * clave de ordenamiento, no la posición en una lista: nadie lee «el número 3», solo el
     * {@code ORDER BY}. Recolocar 1..n en cada borrado costaría las dos pasadas de
     * {@link #renumerar} —el UNIQUE no es aplazable— para arreglar algo que no se ve desde
     * fuera. Quien quiera la lista compacta tiene los endpoints de reordenar.
     */
    private <T> int siguienteOrden(List<T> yaPuestos, Function<T, Integer> ordenDe) {
        return maximoOrden(yaPuestos, ordenDe) + 1;
    }

    private <T> int maximoOrden(List<T> filas, Function<T, Integer> ordenDe) {
        return filas.stream().map(ordenDe).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * Pone las filas en el orden que pide la lista, y se planta si la lista no es el todo.
     *
     * <p>Entera o nada: con una lista parcial habría que inventar dónde va el resto, y ese
     * invento es justo lo que después nadie podría revisar.
     */
    private <T> List<T> elOrdenPedido(List<Long> idsPedidos, List<T> actuales,
                                      Function<T, Long> idDe, String queCosa) {
        Map<Long, T> porId = actuales.stream()
                .collect(Collectors.toMap(idDe, x -> x, (a, b) -> a, LinkedHashMap::new));
        if (idsPedidos.size() != porId.size()
                || new HashSet<>(idsPedidos).size() != idsPedidos.size()
                || !porId.keySet().containsAll(idsPedidos)) {
            throw new IllegalArgumentException(
                    "Para reordenar %s hay que mandar sus %d ids, cada uno una sola vez"
                            .formatted(queCosa, porId.size()));
        }
        return idsPedidos.stream().map(porId::get).toList();
    }

    /**
     * Escribe el orden nuevo en dos pasadas, y no en una.
     *
     * <p>⚠️ El UNIQUE (versión, orden) de la V15 <b>no es aplazable</b>, y cualquier
     * permutación pasa por un instante en que dos filas quieren el mismo número: la base
     * rechaza la primera que llegue. Y no se puede confiar en el orden de los UPDATE —
     * Hibernate lo decide él, y además ejecuta los DELETE al final de cada flush—, así que
     * un renumerado «a la vez» fallaría unas veces sí y otras no. Eso es peor que fallar
     * siempre: pasa las pruebas y revienta en producción.
     *
     * <p>La salida es aparcar antes de colocar. La primera pasada manda todas las filas a un
     * tramo hoy vacío —de {@code max(orden)+1} en adelante— y la segunda escribe el 1..n
     * definitivo, que a esas alturas está entero libre. Ninguna de las dos pisa un número
     * ocupado, así que el orden en que se ejecuten los UPDATE deja de importar. El
     * {@code flush} entre pasadas es lo que las separa de verdad: sin él, Hibernate las
     * junta en una sola y vuelve el problema.
     */
    private <T> void renumerar(List<T> enElOrdenPedido, int aparcarDesde,
                               ObjIntConsumer<T> ponerOrden, JpaRepository<T, ?> repositorio) {
        for (int i = 0; i < enElOrdenPedido.size(); i++) {
            ponerOrden.accept(enElOrdenPedido.get(i), aparcarDesde + i + 1);
        }
        repositorio.saveAll(enElOrdenPedido);
        repositorio.flush();

        for (int i = 0; i < enElOrdenPedido.size(); i++) {
            ponerOrden.accept(enElOrdenPedido.get(i), i + 1);
        }
        repositorio.saveAll(enElOrdenPedido);
        repositorio.flush();
    }

    private void auditar(ContextoUsuario quien, String accion, String entidad, Long entidadId,
                         ReordenarElementos datos) {
        auditoria.registrar(quien.organizacionId(), quien, accion, entidad, entidadId,
                null, Map.of("idsEnOrden", datos.idsEnOrden()), null);
    }

    /**
     * Dos criterios de la misma rúbrica no pueden llevar el mismo código.
     *
     * <p>Lo dice la V10 con un {@code UNIQUE NULLS NOT DISTINCT (codigo,
     * version_plantilla_prueba_id)}, y su error llega como «Ya existe un registro con esos
     * datos», sin nombrar nada: {@code ManejadorErrores} solo sabe sacar el campo culpable
     * cuando la clave es de una columna, y esta es de dos. Aquí se dice cuál es el código.
     *
     * <p>Importa sobre todo al corregir, que es la razón de que exista este método: cambiarle
     * el código a un criterio es justo lo que se hace para arreglar una errata, y toparse con
     * el de al lado es lo más fácil del mundo. {@code exceptoCriterioId} es el que se está
     * editando — chocar consigo mismo no es chocar.
     */
    private void exigirCodigoLibreEnLaRubrica(Long versionId, String codigo, Long exceptoCriterioId) {
        boolean ocupado = criterios.findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .anyMatch(c -> codigo.equals(c.getCodigo()) && !c.getId().equals(exceptoCriterioId));
        if (ocupado) {
            throw new IllegalArgumentException(
                    "Esta rúbrica ya tiene un criterio con el código «%s»".formatted(codigo));
        }
    }

    /**
     * Las dos reglas de tiempo que la V15 comprueba con un CHECK, comprobadas antes.
     *
     * <p>La base ya las tiene, pero su error llega como una violación de integridad con el
     * nombre de la restricción dentro: un 400 que no dice qué campo falta. Aquí lo dice.
     * Importa sobre todo al editar, que es donde alguien puede pasar una prueba de
     * cronometrada a plazo abierto y dejarse la duración atrás.
     */
    private void exigirTiemposCoherentes(CrearVersion datos) {
        if ("CRONOMETRADA".equals(datos.modalidad()) && datos.duracionMinutos() == null) {
            throw new IllegalArgumentException(
                    "Una prueba cronometrada necesita duracionMinutos");
        }
        if ("PLAZO_ABIERTO".equals(datos.modalidad()) && datos.plazoDias() == null) {
            throw new IllegalArgumentException(
                    "Una prueba de plazo abierto necesita plazoDias");
        }
        if (datos.minutoCambioMin() != null && datos.minutoCambioMax() != null
                && datos.minutoCambioMin() > datos.minutoCambioMax()) {
            throw new IllegalArgumentException(
                    "El minuto del cambio va de menor a mayor: minutoCambioMin no puede pasar de minutoCambioMax");
        }
    }

    /**
     * La guía de calificación tiene tope, y se comprueba en tres sitios a propósito.
     *
     * <p>⚠️ Este texto lo escribe una persona y acaba dentro del mensaje {@code system} de un
     * modelo que pone notas que alimentan el ranking. Una guía de cincuenta mil caracteres no
     * es una guía: es un intento de tapar la instrucción del agente por volumen, y de paso
     * cuesta dinero en cada calificación.
     *
     * <p>Los tres sitios no son una duplicación por descuido, cada uno cubre lo que los otros
     * no llegan a ver: el {@code @Size} del contrato REST devuelve un 400 antes de tocar
     * nada; el CHECK de la V46 es el único que hay en los caminos que no pasan por el DTO —la
     * copia entre organizaciones y las cargas por SQL—; y esto de aquí es lo que da un
     * mensaje que se entiende, igual que hace {@link #exigirTiemposCoherentes} con los CHECK
     * de la V15. Hay un cuarto tope, y es el que de verdad protege al modelo: se vuelve a
     * cortar al armar el prompt, en {@code AgentePruebaPuesto}, porque para entonces el texto
     * puede haber entrado por cualquiera de esos caminos.
     */
    private void exigirGuiaRazonable(CrearVersion datos) {
        String guia = datos.guiaCalificacion();
        if (guia != null && guia.length() > MAXIMO_GUIA_CALIFICACION) {
            throw new IllegalArgumentException(
                    "La guía de calificación no puede pasar de %d caracteres; esta trae %d"
                            .formatted(MAXIMO_GUIA_CALIFICACION, guia.length()));
        }
    }

    /**
     * En la auditoría de la guía va su longitud, no su texto.
     *
     * <p>No es por ahorrar sitio: es que el texto que de verdad importa —el que estaba en
     * vigor cuando se calificó a alguien— ya está guardado entero y en otro lado. Una guía
     * solo se edita en BORRADOR, así que la que califica es la que quedó congelada al
     * publicar, y {@code EjecucionIa.envio} guarda el {@code system} completo de cada llamada
     * al modelo: abrir una nota de hace seis meses enseña la guía con la que se puso. Repetir
     * aquí dos mil caracteres en cada retoque del borrador no añadiría nada a eso.
     */
    private String largoDe(String guia) {
        return guia == null ? "0" : String.valueOf(guia.length());
    }

    // RF-83: entre 8 y 10 universales, entre 3 y 5 específicas. No hay tope de previas.
    //
    // La cuota rige solo cuando la prueba pide entregables: sus preguntas existen para que
    // el candidato defienda lo que produjo. Una versión sin entregables es un cuestionario
    // —las preguntas SON la prueba, como el técnico de Administrador— y ahí la cuota no
    // tiene sentido; basta con que haya al menos una pregunta que responder.
    private void validarCuotaPreguntas(Long versionId) {
        List<Long> ids = preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(versionId).stream()
                .map(PreguntaVersionPlantilla::getPreguntaPruebaId).toList();

        if (entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(versionId).isEmpty()) {
            if (ids.isEmpty()) {
                throw new IllegalArgumentException(
                        "Una prueba sin entregables es un cuestionario: necesita al menos una pregunta");
            }
            return;
        }
        Map<String, Long> porTipo = preguntasCatalogo.findByIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(PreguntaPrueba::getTipo, java.util.stream.Collectors.counting()));

        long universales = porTipo.getOrDefault("UNIVERSAL", 0L);
        long especificas = porTipo.getOrDefault("ESPECIFICA", 0L);
        if (universales < UNIVERSALES_MIN || universales > UNIVERSALES_MAX) {
            throw new IllegalArgumentException(
                    "Hacen falta entre %d y %d preguntas universales; hay %d"
                            .formatted(UNIVERSALES_MIN, UNIVERSALES_MAX, universales));
        }
        if (especificas < ESPECIFICAS_MIN || especificas > ESPECIFICAS_MAX) {
            throw new IllegalArgumentException(
                    "Hacen falta entre %d y %d preguntas específicas del puesto; hay %d"
                            .formatted(ESPECIFICAS_MIN, ESPECIFICAS_MAX, especificas));
        }
    }

    // RF-86/89: la rúbrica también suma 100, y se comprueba al publicar, no al guardar.
    private void validarRubrica(Long versionId) {
        BigDecimal suma = criterios.findByVersionPlantillaPruebaId(versionId).stream()
                .map(Criterio::getPuntos)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.subtract(BigDecimal.valueOf(100)).abs().compareTo(TOLERANCIA) > 0) {
            throw new IllegalArgumentException("La rúbrica debe sumar 100 puntos (hoy suma " + suma + ")");
        }
    }
}
