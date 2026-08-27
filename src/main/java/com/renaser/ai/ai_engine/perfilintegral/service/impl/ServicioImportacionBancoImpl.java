package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.DimensionResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.BancoLeido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaCampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaOpcion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.FilaRango;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ResultadoImportacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.CampoCaso;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.RangoPregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CampoCasoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.DimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RangoPreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.ImportacionInvalidaException;
import com.renaser.ai.ai_engine.perfilintegral.service.LectorBancoCazatalentos;
import com.renaser.ai.ai_engine.perfilintegral.service.LectorPlantillaBanco;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioImportacionBanco;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Del archivo a la base, en una sola transacción y por lotes.
 *
 * <p>No pasa por {@code crearPregunta}/{@code agregarOpcion}: fila a fila serían cientos
 * de lecturas de guarda y cientos de filas de auditoría para un banco de 190 preguntas,
 * sin ganar nada — lo que esas guardas comprueban ya lo comprobó el lector sobre el
 * archivo completo. Aquí se inserta con {@code saveAll} por familia y se deja UNA fila
 * de auditoría con el resumen: quién importó qué archivo y cuánto trajo.
 *
 * <p>Que conste lo que {@code saveAll} <b>no</b> hace: agrupar los INSERT. Los ids son
 * IDENTITY y con eso Hibernate desactiva el batching, así que siguen siendo tantas
 * sentencias como filas. Lo que se ahorra son las guardas repetidas y las 600 filas de
 * auditoría, y eso ya vale el camino aparte.
 */
@Service
@RequiredArgsConstructor
public class ServicioImportacionBancoImpl implements ServicioImportacionBanco {

    private static final String LETRAS = "abcdefghijklmnopqrstuvwxyz";

    private final LectorPlantillaBanco lector;
    private final LectorBancoCazatalentos lectorCazatalentos;
    private final VersionBancoRepository versiones;
    private final PreguntaRepository preguntas;
    private final OpcionRepository opciones;
    private final RangoPreguntaRepository rangos;
    private final CampoCasoRepository camposCaso;
    private final ParConsistenciaRepository pares;
    private final DimensionRepository dimensiones;
    private final PreguntaDimensionRepository preguntaDimensiones;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public ResultadoImportacion importar(ContextoUsuario quien, String nivelPuestoCodigo,
                                         String etiqueta, String nombreArchivo,
                                         byte[] archivo) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new IllegalArgumentException("La versión necesita una etiqueta: es el "
                    + "nombre con que el equipo la verá en el panel");
        }
        if (nombreArchivo == null || !nombreArchivo.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("El archivo debe ser la plantilla del "
                    + "banco en formato .xlsx");
        }
        if (archivo == null || archivo.length == 0) {
            throw new IllegalArgumentException("El archivo llegó vacío");
        }

        // Dos formatos de archivo, dos lectores. El del banco CAZATALENTOS se delata por su
        // hoja «Prueba RENASER»; cualquier otro libro sigue el camino de la plantilla v3.
        boolean esCazatalentos = lectorCazatalentos.esSuyo(archivo);

        BancoLeido leido;
        if (esCazatalentos) {
            leido = lectorCazatalentos.leer(new ByteArrayInputStream(archivo));
        } else {
            // El índice nombre/código → código, con la misma normalización que usa el lector
            // para buscar: «Integridad», «INTEGRIDAD» e «int» dan todos INT.
            Map<String, String> indice = new HashMap<>();
            for (var d : dimensiones.findAllByOrderByOrden()) {
                indice.put(LectorPlantillaBanco.normalizar(d.getCodigo()), d.getCodigo());
                indice.put(LectorPlantillaBanco.normalizar(d.getNombre()), d.getCodigo());
            }
            leido = lector.leer(new ByteArrayInputStream(archivo), indice);
        }
        if (!leido.errores().isEmpty()) {
            // Nada se insertó: el lector corre antes de tocar cualquier repositorio.
            throw new ImportacionInvalidaException(leido.errores());
        }

        VersionBanco version = versiones.save(VersionBanco.builder()
                .organizacionId(quien.organizacionId())
                // La plantilla es por rol: un archivo = un nivel de puesto. Los bancos
                // de ALINEACION no viajan por Excel.
                .tipoBanco("NIVEL")
                .nivelPuestoCodigo(nivelPuestoCodigo)
                .etiqueta(etiqueta)
                .estado("BORRADOR")
                // El discriminador del motor: CRITERIOS puntúa contando C1..C4; NULL, el
                // de claves versionadas de siempre.
                .metodoCalificacion(esCazatalentos ? "CRITERIOS" : null)
                .creadoEn(Instant.now())
                .build());

        Instant ahora = Instant.now();

        // Preguntas primero, para tener el mapa código → id que el resto necesita.
        List<Pregunta> filasPregunta = new ArrayList<>();
        int orden = 0;
        for (FilaPregunta p : leido.preguntas()) {
            filasPregunta.add(Pregunta.builder()
                    .versionBancoId(version.getId())
                    .codigo(p.codigo())
                    .tipo(p.tipo())
                    .enunciado(p.enunciado())
                    .situacion(p.situacion())
                    .logicaInterna(p.logicaInterna())
                    // El peso manda: 0 no suma y por tanto no puntúa. Es la única
                    // lectura coherente con validarCoherencia, que rechaza ambas
                    // contradicciones.
                    .esPuntuable(p.peso() > 0)
                    .orden(++orden)
                    .peso(p.peso())
                    .esClave(false)
                    .esEliminatorio(p.esEliminatoria())
                    .casosPedidos(p.casosPedidos())
                    .formulaPuntaje(p.formulaPuntaje())
                    .rangosDePreguntaCodigo(p.rangosDePreguntaCodigo())
                    .c3Esperado(p.c3Esperado())
                    .c4Esperado(p.c4Esperado())
                    .senalDeCero(p.senalDeCero())
                    .creadoEn(ahora)
                    .build());
        }
        Map<String, Long> idPorCodigo = new HashMap<>();
        for (Pregunta guardada : preguntas.saveAll(filasPregunta)) {
            idPorCodigo.put(guardada.getCodigo(), guardada.getId());
        }

        // Las letras no vienen en la plantilla: a, b, c… en el orden del archivo, que
        // es el orden en que el documento las muestra.
        Map<String, Integer> cuentaOpciones = new HashMap<>();
        List<Opcion> filasOpcion = new ArrayList<>();
        for (FilaOpcion o : leido.opciones()) {
            int n = cuentaOpciones.merge(o.codigoPregunta(), 1, Integer::sum);
            filasOpcion.add(Opcion.builder()
                    .preguntaId(idPorCodigo.get(o.codigoPregunta()))
                    .letra(String.valueOf(LETRAS.charAt(n - 1)))
                    .texto(o.texto())
                    .puntaje(o.puntaje() == null ? null : BigDecimal.valueOf(o.puntaje()))
                    .valor(o.valor())
                    .esDistractor(o.esDistractor())
                    .ordenCorrecto(o.ordenCorrecto())
                    .creadoEn(ahora)
                    .build());
        }
        opciones.saveAll(filasOpcion);

        Map<String, Integer> cuentaCampos = new HashMap<>();
        List<CampoCaso> filasCampo = new ArrayList<>();
        for (FilaCampoCaso c : leido.camposCaso()) {
            filasCampo.add(CampoCaso.builder()
                    .preguntaId(idPorCodigo.get(c.codigoPregunta()))
                    .orden(cuentaCampos.merge(c.codigoPregunta(), 1, Integer::sum))
                    .etiqueta(c.etiqueta())
                    .validacion(c.validacion())
                    .creadoEn(ahora)
                    .build());
        }
        camposCaso.saveAll(filasCampo);

        Map<String, Integer> cuentaRangos = new HashMap<>();
        List<RangoPregunta> filasRango = new ArrayList<>();
        for (FilaRango r : leido.rangos()) {
            filasRango.add(RangoPregunta.builder()
                    .preguntaId(idPorCodigo.get(r.codigoPregunta()))
                    .orden(cuentaRangos.merge(r.codigoPregunta(), 1, Integer::sum))
                    .condicion(r.condicion())
                    .puntaje(r.puntaje())
                    .generaBandera(r.generaBandera())
                    .creadoEn(ahora)
                    .build());
        }
        rangos.saveAll(filasRango);

        // Los pares llegan con códigos; los ids existen recién desde el saveAll de arriba.
        List<ParConsistencia> filasPar = new ArrayList<>();
        for (var par : leido.pares()) {
            filasPar.add(ParConsistencia.builder()
                    .versionBancoId(version.getId())
                    .preguntaAId(idPorCodigo.get(par.codigoA()))
                    .preguntaBId(idPorCodigo.get(par.codigoB()))
                    .penalizacionPorcentaje(par.penalizacionPorcentaje())
                    .separacionMinimaItems(par.separacionMinimaItems())
                    .condicion(par.condicion())
                    .creadoEn(ahora)
                    .build());
        }
        pares.saveAll(filasPar);

        List<PreguntaDimension> filasDimension = new ArrayList<>();
        for (FilaPregunta p : leido.preguntas()) {
            for (String codigoDimension : p.dimensiones()) {
                filasDimension.add(PreguntaDimension.builder()
                        .preguntaId(idPorCodigo.get(p.codigo()))
                        .dimensionCodigo(codigoDimension)
                        .creadoEn(ahora)
                        .build());
            }
        }
        preguntaDimensiones.saveAll(filasDimension);

        ResultadoImportacion resultado = new ResultadoImportacion(version.getId(),
                etiqueta, filasPregunta.size(), filasOpcion.size(), filasCampo.size(),
                filasRango.size(), filasPar.size(), filasDimension.size());

        // Una sola fila de auditoría con el resumen: el detalle está en el borrador
        // mismo, que se puede revisar pregunta a pregunta antes de publicar.
        auditoria.registrar(quien.organizacionId(), quien, "importar_banco_excel",
                "version_banco", version.getId(), null,
                Map.of("archivo", nombreArchivo,
                        "nivelPuestoCodigo", nivelPuestoCodigo,
                        "preguntas", resultado.preguntas(),
                        "opciones", resultado.opciones(),
                        "camposCaso", resultado.camposCaso(),
                        "rangos", resultado.rangos(),
                        "pares", resultado.pares(),
                        "dimensiones", resultado.dimensionesAsignadas()), null);
        return resultado;
    }

    @Override
    public List<DimensionResponse> listarDimensiones(ContextoUsuario quien) {
        permisos.alcanceDe("ver_banco_preguntas");
        return dimensiones.findAllByOrderByOrden().stream()
                .map(d -> new DimensionResponse(d.getCodigo(), d.getNombre(),
                        d.getDefinicion(), d.getOrden()))
                .toList();
    }
}
