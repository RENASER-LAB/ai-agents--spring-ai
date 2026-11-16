package com.renaser.ai.ai_engine.informe.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * El informe de una etapa de un candidato, armado una vez y pintado dos: como página en el
 * visor del panel y como PDF. Los dos leen exactamente estos datos.
 *
 * <p><b>Nunca lleva</b> la pretensión salarial, el celular, la foto, la edad, el sexo, el estado
 * civil ni el contenido de un entregable. Del candidato van el nombre y el correo.
 *
 * <p><b>Nada viene recortado.</b> Cada texto llega entero; quien lo pinta decide cómo enseñarlo
 * (el PDF lo imprime todo, el visor lo despliega).
 */
public final class DtosInforme {

    private DtosInforme() {
    }

    /**
     * @param etapa         CV · EVALUACION · PRUEBA
     * @param titulo        «Informe del currículum», «Informe de la evaluación»…
     * @param nota          la de la etapa, una sola vez (el anillo)
     * @param grupo         el grupo de prioridad (ALTA, POTENCIAL_CON_RIESGO…) o nulo
     * @param datosClave    lo que va en la columna oscura bajo el anillo
     * @param recuentos     en el CV: demostradas, no cuadran y por verificar
     * @param experiencia   solo en el CV
     * @param resumen       «Lo que muestra su CV» o «En pocas líneas»; nulo si no hay texto
     * @param evaluado      «Qué se evaluó»
     * @param bloques       los bloques del resumen, en orden
     * @param detalle       «El porqué de cada nota»; vacío si ya está todo bajo las barras
     * @param anexo         las preguntas y respuestas; nulo en el CV
     * @param aviso         lo que hay que decir antes que nada (un CV que no se pudo leer)
     * @param textosEnCamino el redactor está escribiendo los textos; mientras, van las explicaciones
     */
    public record Informe(
            String etapa,
            String titulo,
            String empresa,
            String vacante,
            Long postulacionId,
            String candidato,
            String correo,
            Instant generadoEn,
            Nota nota,
            String grupo,
            List<Dato> datosClave,
            List<Recuento> recuentos,
            Experiencia experiencia,
            Resumen resumen,
            Evaluado evaluado,
            List<Bloque> bloques,
            List<Enlace> enlaces,
            String detalleTitulo,
            List<Detalle> detalle,
            String detallePie,
            Anexo anexo,
            String aviso,
            boolean textosEnCamino,
            String nombreArchivo) {
    }

    /**
     * @param valor  la nota, o nula si todavía no la hay (se pinta «—» o «pendiente», nunca 0)
     * @param texto  cómo se escribe: «74», «3,5», «pendiente»
     * @param sobre  «de 100»
     * @param base   en qué se basa, en una frase
     */
    public record Nota(BigDecimal valor, String texto, String sobre, String base) {
    }

    public record Dato(String etiqueta, String valor) {
    }

    /** @param tono BIEN · MAL · PENDIENTE */
    public record Recuento(String etiqueta, int cuantos, String tono) {
    }

    /** Lo más destacado del CV. Un dato que falta dice «sin dato». */
    public record Experiencia(String total, String totalDetalle, String ultimoPuesto,
                              String ultimoDetalle, String formacion, List<String> habilidades,
                              String perfil) {
    }

    public record Resumen(String titulo, String texto) {
    }

    public record Evaluado(String titulo, String escala, List<Criterio> criterios, String pie) {
    }

    /**
     * Un criterio con su barra.
     *
     * @param notaTexto   la nota, una sola vez en todo el informe; «—» si falta
     * @param maximoTexto «/ 25» cuando no es sobre 100, o nulo
     * @param porcentaje  lo que llena la barra, de 0 a 100; nulo sin nota
     * @param quien       quién la puso: IA, Persona o Sistema; nulo si no se sabe
     * @param texto       lo que va bajo la barra: la línea del redactor o, sin ella, la
     *                    explicación completa
     * @param explicacion la explicación de la IA, entera (el visor la enseña al desplegar)
     * @param preguntas   «3 cerradas · 2 abiertas», en la evaluación
     * @param pendiente   por qué no tiene nota todavía
     */
    public record Criterio(String nombre, BigDecimal nota, BigDecimal maximo, String notaTexto,
                           String maximoTexto, Integer porcentaje, String quien,
                           boolean ajustada, String motivoAjuste, String texto,
                           String explicacion, String preguntas, String pendiente) {
    }

    /**
     * Un bloque del resumen.
     *
     * @param tono BIEN (✓ verde) · NEUTRO (flecha gris) · MAL (aspa roja) · PENDIENTE (bocadillo
     *             azul sobre bruma) · CV (lo que pidió el CV)
     * @param vacio lo que se dice si no hay ningún punto («Sin datos»)
     */
    public record Bloque(String codigo, String titulo, String tono, String vacio,
                         List<Punto> puntos) {
    }

    /**
     * @param detalle  lo que va debajo: la pregunta de validación, la sugerencia de la IA
     * @param origen   CV · EVALUACION · PRUEBA, en lo pendiente
     * @param etiqueta «Por contrastar», «Declarada», «Sin información»…
     * @param grupo    «De la prueba», «De la evaluación», «Del CV» en lo pendiente de la entrevista
     */
    public record Punto(String texto, String detalle, String origen, String etiqueta, String grupo) {
    }

    /** @param tipo CV · LINKEDIN · GITHUB · WEB */
    public record Enlace(String rotulo, String url, String tipo) {
    }

    /** Una entrada de «El porqué de cada nota». */
    public record Detalle(Integer numero, String titulo, String meta, String notaTexto,
                          boolean ajustada, String motivoAjuste, String explicacion,
                          String evidencia, List<Senal> senales, String pendiente) {
    }

    public record Senal(String nombre, boolean cumple) {
    }

    public record Anexo(String titulo, String subtitulo, String caso,
                        List<PreguntaAnexo> preguntas, String entregablesResumen,
                        List<EntregableAnexo> entregables) {
    }

    /**
     * @param respuesta     el texto, o la opción elegida; nulo si la dejó en blanco
     * @param loQueDemostro la línea del redactor, o la explicación de la IA si no la hay
     */
    public record PreguntaAnexo(int numero, String enunciado, String meta, String respuesta,
                                String loQueDemostro, String explicacion) {
    }

    /** Del entregable va el nombre y cuándo llegó; nunca su contenido. */
    public record EntregableAnexo(String nombre, String detalle, boolean entregado) {
    }

    // ============================================================ lo que no es el informe

    /**
     * Si un informe se puede descargar ya y, si no, por qué.
     *
     * @param motivo «Todavía no tiene calificación», «La IA está calificando» o «La calificación
     *               falló»; nulo si está disponible
     */
    public record EstadoDeUnInforme(String etapa, boolean disponible, String motivo,
                                    BigDecimal nota) {
    }

    /** Los informes que quien pregunta puede ver de una postulación. Sin permiso, no sale. */
    public record InformesDelPostulante(Long postulacionId, String candidato,
                                        List<EstadoDeUnInforme> informes) {
    }

    /** Un postulante en la lista del visor. */
    public record PostulanteDelVisor(Long postulacionId, int puesto, String candidato,
                                     String grupo, BigDecimal notaCv,
                                     BigDecimal notaEvaluacion, BigDecimal notaPrueba) {
    }

    /** La lista del visor, en el orden del ranking, y qué etapas puede ver quien pregunta. */
    public record VisorDeLaVacante(Long vacanteId, String vacante, String puesto,
                                   List<PostulanteDelVisor> postulantes, List<String> etapas) {
    }

    /**
     * Un archivo ya armado: el PDF o el .zip, con su nombre.
     *
     * <p>Lleva un arreglo, así que la igualdad se compara por contenido y el texto dice cuántos
     * bytes trae en vez de volcarlos (un PDF ocupa cientos de KB).
     */
    public record ArchivoArmado(String nombre, String tipo, byte[] contenido) {

        @Override
        public boolean equals(Object otro) {
            return this == otro || otro instanceof ArchivoArmado o
                    && Objects.equals(nombre, o.nombre) && Objects.equals(tipo, o.tipo)
                    && Arrays.equals(contenido, o.contenido);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hash(nombre, tipo) + Arrays.hashCode(contenido);
        }

        @Override
        public String toString() {
            return "ArchivoArmado[nombre=" + nombre + ", tipo=" + tipo + ", contenido="
                    + (contenido == null ? "null" : contenido.length + " bytes") + "]";
        }
    }
}
