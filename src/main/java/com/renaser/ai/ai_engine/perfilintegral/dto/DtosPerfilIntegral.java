package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lo que el panel enseña del Perfil Integral de un candidato.
 *
 * <p>Es de <b>solo lectura</b>: aquí no se ajusta ninguna nota. Cambiar una nota tiene su
 * propio camino, porque exige motivo escrito y deja rastro de quién la cambió.
 *
 * <p>Estos contratos son del panel del equipo, no del portal. El candidato nunca ve su
 * puntaje ni la explicación del modelo.
 */
public final class DtosPerfilIntegral {

    private DtosPerfilIntegral() {}

    /**
     * El retrato completo. Puede llegar a medias y eso es información, no un error: si la
     * IA todavía no ha corrido, {@code perfil} viene vacío y {@code estadoCalificacion}
     * dice por qué.
     */
    public record PerfilIntegralResponse(
            Long postulacionId,
            String estadoCalificacion,
            String resumen,
            BigDecimal adecuacion,
            BigDecimal potencial,
            BigDecimal altoRendimiento,
            BigDecimal confianzaEvidencia,
            BigDecimal notaEtapa,
            Instant actualizadoEn,
            List<HallazgoResponse> hallazgos,
            List<NotaCriterioResponse> notasCriterio,
            List<AlertaResponse> alertas) {}

    // El tipo no es decorativo: la Regla 1 del documento 03 prohíbe mezclarlos. Un riesgo
    // que se puede corregir y una falta de evidencia no son lo mismo, y el panel los pinta
    // distinto para que nadie los confunda al decidir.
    public record HallazgoResponse(String tipo, String descripcion, String evidencia,
                                   boolean esCanalizable, String sugerencia) {}

    // La explicación viaja siempre: una nota sin ella no se guarda, así que tampoco se
    // enseña sola. Quien revisa tiene que poder ver en qué se basó el modelo.
    // El peso viaja con la nota, y no es decoracion: es lo que explica de donde sale el
    // numero final. Un 90 en un criterio que pesa 25 y un 90 en uno que pesa 5 se leen
    // igual en pantalla y no valen lo mismo.
    // confianza va de 0 a 100, igual que el puntaje —no de 0 a 1—. Lo fija el prompt del
    // agente («numero de 0 a 100») y lo hace cumplir PuenteCalificacionIaImpl, que acota a
    // CIEN antes de guardar. La columna numeric(5,2) no lo demuestra por sí sola: admitiría
    // un 999,99. Quien la pinte no tiene que multiplicar por nada.
    //
    // motivoAjuste no-nulo significa exactamente una cosa: esta nota la corrigió una
    // persona. Lo garantiza un CHECK en nota_criterio —ajustada_por_usuario_id sin motivo
    // no entra—, así que basta mirar este campo para saber que hubo mano humana detrás.
    //
    // codigo es el nombre CORTO del criterio, y viaja porque el largo no cabe donde hay
    // que compararlos. Una tabla con una columna por criterio pone «Resultados
    // demostrables» encima de una celda que dice «40»: la cabecera decide el ancho de la
    // columna y el ancho decide si la tabla entera cabe en una pantalla. El codigo ya
    // existe en la tabla `criterio` —CV_RESULTADOS, CAJA, DIVISAS— y es estable dentro de
    // su rubrica, que es lo que un rotulo de columna necesita.
    //
    // Va ADEMAS del nombre, no en su lugar: el corto rotula y el largo explica. Quien
    // pinte una columna estrecha usa el codigo y deja el nombre para el titulo emergente.
    public record NotaCriterioResponse(String criterio, String codigo, BigDecimal puntaje,
                                       BigDecimal maximo, BigDecimal peso,
                                       String explicacion, String origen,
                                       BigDecimal confianza, String motivoAjuste) {}

    // Una alerta no descarta a nadie: es una pregunta para la conversación final.
    public record AlertaResponse(String tipo, String descripcion, Instant creadoEn) {}

    /** Lo que responde pedir que se vuelva a calificar. */
    public record CalificacionEncoladaResponse(String estado, String mensaje) {}

    /**
     * Quién es el candidato, sacado de su currículum por el agente que no puntúa.
     *
     * <p>No lleva edad, sexo ni estado civil: el agente lee la versión recortada del
     * currículum y esos datos no le llegan.
     */
    public record DatosCandidato(String nombre, String email, String telefono,
                                 String perfilResumen, String habilidades,
                                 Integer experienciaMesesTotal, String ultimoPuesto,
                                 String ultimaEmpresa, String educacionMaxima) {}

    /** Lo que responde pedir una pasada sobre la tanda entera. */
    public record PasadaEncolada(String estado, int candidatos, String mensaje) {}

    /**
     * La tanda de una convocatoria, ordenada de más apto a menos.
     *
     * <p>Es la pantalla que contesta «¿a quién invito primero?». Manda el grupo de
     * prioridad y no la nota: alguien con 92 y un riesgo crítico no va por delante de
     * alguien con 88 y ninguno, y ordenar solo por número escondería justo eso.
     */
    /**
     * Lo que devuelve reabrir una evaluación: hasta cuándo tiene ahora, y en qué estado
     * quedó la postulación.
     */
    public record EvaluacionReabierta(Long postulacionId, String estado,
                                      java.time.Instant venceEn, int diasDePlazo) {}

    /**
     * La tanda entera de una vacante, ordenada de más apto a menos.
     *
     * <p><b>Antes de usar este orden para decidir, mira {@code conPasadaFina} contra
     * {@code total}.</b> No es un dato de progreso: es lo que dice si esta lista sirve para
     * decidir o solo para ordenar.
     *
     * <p>Hay dos pasadas y no dan la misma nota. La rápida usa el modelo barato, que no
     * razona, y existe para contestar «llegaron cien currículums, a quién invito primero».
     * La fina usa el modelo que razona y pisa las notas provisionales. Una lista donde
     * {@code conPasadaFina} es mucho menor que {@code total} está ordenada por el modelo que
     * el propio sistema declara provisional, y en pantalla se ve exactamente igual que una
     * definitiva. Ya pasó: una vacante se rankeó entera con la rápida y nadie lo notó hasta
     * que un candidato con nota fina apareció hundido entre diecinueve notas rápidas —no
     * porque fuera peor, sino porque se le midió con otra vara.
     *
     * <p>Quien pinte esta lista <b>tiene que decirlo</b> cuando las dos cifras no coincidan.
     * Mezclar notas de las dos pasadas en un mismo orden no significa nada.
     *
     * <p>Los otros tres números cuentan cómo va la calificación, no su calidad:
     * {@code calificados} los que ya tienen retrato, {@code enCurso} los que la IA está
     * mirando ahora, y {@code fallidos} aquellos en los que falló y <b>no se les inventó una
     * nota</b> —normalmente un currículum escaneado, del que no se puede sacar texto—.
     * <p>{@code puedeVerPretension} dice si esta petición pudo siquiera consultar el sueldo
     * que pide cada candidato. Viaja porque sin él una columna vacía tiene dos lecturas que
     * no se distinguen desde el navegador —nadie lo declaró, o tu rol no puede verlo— y la
     * pantalla se ve obligada a nombrar las dos sin afirmar ninguna.
     */
    public record RankingVacante(
            Long vacanteId,
            String vacante,
            String puesto,
            String nivelPuesto,
            int total,
            int conPasadaFina,
            int calificados,
            int enCurso,
            int fallidos,
            boolean puedeVerPretension,
            List<FilaRanking> filas) {}

    /**
     * Lo que el candidato lleva rendido, sobre 100.
     *
     * <p>El embudo son cuatro etapas y solo dos han ocurrido a estas alturas: el Perfil
     * Integral —que por dentro es el currículum y el banco de preguntas— y la prueba del
     * puesto. Juntas no llegan a 100 —en la v4 son 70—, así que la cifra se reescala entre
     * la suma de ESOS dos pesos y no entre 100. Sirve para comparar candidatos entre sí
     * antes de que exista la nota global; no la sustituye ni se guarda en ninguna parte.
     *
     * <p>{@code sobre100} viene vacío mientras falte cualquiera de las dos notas: media
     * cifra reescalada parece comparable con la de otro candidato y no lo es. Las partes
     * del desglose se pueden quedar vacías por su cuenta sin que eso anule el total.
     *
     * <p><b>El desglose no trae la nota del banco de preguntas suelta, y no es un olvido.</b>
     * No se guarda en ninguna parte: lo que se guarda es la mezcla ya hecha con el
     * currículum. Despejarla restando parece fácil y da un número falso en dos casos
     * reales —quien no tiene evaluación asignada, cuyo Perfil Integral ES su nota de
     * currículum, y las vacantes que califica {@code CalificacionCriterios}, que escriben
     * ahí un índice de pilares que no es CV + banco—. Así que se enseña el Perfil Integral
     * entero, que es exacto, y con estas tres cifras el ponderado se puede rehacer a mano.
     *
     * @param sobre100 la cifra reescalada; vacía si falta alguna de las dos notas de etapa
     * @param cv       la nota del currículum, tal como se calcula para el ranking
     * @param perfil   la nota del Perfil Integral, que ya incluye la del banco
     * @param prueba   la nota de la prueba del puesto
     */
    public record Ponderado(
            BigDecimal sobre100,
            BigDecimal cv,
            BigDecimal perfil,
            BigDecimal prueba) {}

    /**
     * Un candidato en la tanda. Los números pueden venir vacíos y eso es información: la
     * IA todavía no llegó a esa fila, o falló y no se le inventó una nota.
     */
    public record FilaRanking(
            int puesto,
            Long postulacionId,
            String uuid,
            String candidato,
            String correo,
            String estado,
            String estadoNombre,
            String estadoCalificacion,
            // FINA, RAPIDA o vacío. Una nota de la rápida es provisional.
            String pasada,
            // Cómo se llama su archivo. Es lo que permite dar con el currículum
            // en la carpeta donde vive, sin tener que servirlo desde aquí.
            String archivoNombre,
            DatosCandidato datos,
            String grupoPrioridad,
            BigDecimal notaEtapa,
            BigDecimal notaCurriculum,
            BigDecimal adecuacion,
            BigDecimal potencial,
            BigDecimal altoRendimiento,
            BigDecimal confianzaEvidencia,
            String resumen,
            int riesgosCriticos,
            int fortalezas,
            int alertas,
            Instant actualizadoEn,
            List<NotaCriterioResponse> notasCriterio,
            // Dónde vive, ya escrito para leer: «Arequipa — Camaná». Sale de
            // persona.ciudad_ubigeo, no del texto libre del perfil, para que filtrar por
            // ciudad compare códigos y no las seis formas de escribir «Lima».
            String ciudad,
            String ciudadCodigo,
            // La pretensión SOLO viaja con el permiso ver_pretension; sin él los tres
            // vienen en null. La V36 lo dejó escrito: si apareciera junto a la nota para
            // todo el mundo, pesaría en la decisión, que es justo lo que se evita.
            BigDecimal pretensionMin,
            BigDecimal pretensionMax,
            String pretensionMoneda,
            // Lo ya rendido sobre 100. Es un objeto y no cuatro cifras sueltas a propósito:
            // este record se copia campo a campo al numerar las filas, y cuatro BigDecimal
            // vecinos son cuatro ocasiones de intercambiar dos sin que el compilador chiste.
            Ponderado ponderado) {}

    // ============ El desglose de la evaluación del banco ============

    /**
     * La evaluación del banco, abierta por dentro.
     *
     * <p>Puede llegar a medias y eso es información: sin evaluación asignada todo viene
     * vacío, y una entregada pero aún sin calificar trae las respuestas sin nota. Nunca es
     * un 404 — una evaluación sin calificar es un estado normal del proceso.
     *
     * <p>{@code notaEvaluacion} va sobre 100 y pondera lo cerrado y lo abierto por cuántas
     * preguntas produjo cada mitad — el mismo cálculo con el que esa nota entra en la etapa.
     */
    public record DesgloseEvaluacion(
            Long postulacionId,
            String estado,
            Instant entregadaEn,
            BigDecimal notaEvaluacion,
            ResumenCerradas cerradas,
            List<RespuestaAbiertaVista> abiertas,
            List<AlineacionVista> alineacion,
            /**
             * Lo que solo se ve mirando el cuestionario entero, no una respuesta.
             *
             * <p>Vacía cuando el banco no medía las señales, y vacía también cuando las
             * medía y no hay ningún patrón: las dos cosas se leen igual y está bien, porque
             * el bloque de señales de cada respuesta ya dice cuál de los dos casos es.
             */
            List<PatronDelCuestionario> patrones) {}

    /**
     * Un patrón del cuestionario completo.
     *
     * <p>Son consultas sobre las señales ya guardadas, no otra pasada de IA — es
     * literalmente para lo que la V41 creó esas columnas. No descartan a nadie: son dos
     * preguntas para la conversación final, como las alertas.
     */
    public record PatronDelCuestionario(String codigo, String titulo, String descripcion,
                                        int deCuantas, int total) {}

    /** Lo cerrado no se desglosa por pregunta: se corrige solo y sale como un promedio sobre 100. */
    public record ResumenCerradas(BigDecimal nota, int preguntas) {}

    /**
     * Una respuesta abierta con la nota que le puso la IA. {@code puntaje} va de 0 a 4.
     * {@code motivoAjuste} solo tiene valor si un humano corrigió la nota — y entonces
     * el porqué es obligatorio, igual que en el resto del sistema.
     */
    public record RespuestaAbiertaVista(
            String pregunta,
            String formato,
            String respuesta,
            BigDecimal puntaje,
            String explicacion,
            String evidenciaCitada,
            BigDecimal confianza,
            String motivoAjuste,
            /**
             * Qué pilar alimenta esta respuesta, dicho para leer —«Iniciativa»— y su
             * código. Vacíos si la pregunta no cuelga de ningún pilar.
             *
             * <p>Viajan porque sin ellos las respuestas abiertas son una lista plana y no
             * se puede saber cuáles sostienen un pilar concreto. El vínculo existe desde
             * la V41 en {@code pregunta_dimension}; lo que faltaba era enseñarlo.
             */
            String pilar,
            String pilarCodigo,
            /**
             * Las cuatro señales que el agente marcó, y la de cero.
             *
             * <p>Lo calcula el código y no la aritmética del modelo. Enseñar el número
             * sin ellas deja «3 de 4» sin decir cuál faltó, que es justo lo que hay que
             * poder discutir con la persona.
             *
             * <p>⚠️ <b>El puntaje NO es el conteo de las cuatro, y confundirlo hace que un
             * cero parezca un error de cálculo.</b> Ver {@code FormulasCazatalentos.puntaje}:
             * {@code episodio} es una PUERTA —sin él el puntaje es 0 aunque las otras tres
             * estén marcadas—, {@code cumpleSenalCero} también fuerza el 0, y cuando falta
             * {@code dato} la pregunta puede declarar un tope que recorta el resultado
             * (regla dura R11). Con la puerta abierta, el puntaje es 1 más las otras tres
             * que estén.
             *
             * <p>⚠️ <b>Nulas no significa «ninguna se cumplió»: significa que ese banco no
             * las medía.</b> Solo el banco CAZATALENTOS puntúa así; las notas de los bancos
             * anteriores las tienen vacías y no se inventan. Quien las pinte tiene que
             * distinguir las dos cosas o convertirá una evaluación antigua en un cero.
             */
            Senales senales) {}

    /**
     * Las cuatro señales de una respuesta del banco CAZATALENTOS, presentes o ausentes.
     *
     * <p>Es un tipo propio y no cuatro campos sueltos para que el nulo sea uno solo: o el
     * banco las medía —y entonces están las cuatro— o no las medía y no hay ninguna. Cuatro
     * booleanos sueltos admitirían tres puestas y una vacía, que no significa nada.
     */
    public record Senales(
            boolean episodio,
            boolean autoria,
            boolean dato,
            boolean incomodidad,
            Boolean cumpleSenalCero) {}

    /** Un bloque de alineación personal con su semáforo. Un rojo no descarta a nadie por sí solo. */
    public record AlineacionVista(String bloque, String semaforo, String explicacion) {}

}
