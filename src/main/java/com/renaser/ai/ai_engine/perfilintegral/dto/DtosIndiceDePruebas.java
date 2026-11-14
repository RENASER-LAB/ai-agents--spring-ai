package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.time.Instant;

/**
 * Los contratos de «Pruebas»: el índice de las preguntas y pruebas que la empresa escribió en
 * el editor de sus vacantes (`GET /api/v1/panel/pruebas`). Es de solo lectura: lo que se copia
 * viaja por el {@code POST …/copia} de siempre.
 *
 * <p>El panel no sabe sus permisos: si una fila se puede usar en otra vacante viaja en la fila.
 */
public final class DtosIndiceDePruebas {

    private DtosIndiceDePruebas() {
    }

    /** La situación de la vacante: {@code vacante.estado} más {@code archivada_en}. */
    public static final String SIN_PUBLICAR = "SIN_PUBLICAR";
    public static final String PUBLICADA = "PUBLICADA";
    public static final String CERRADA = "CERRADA";
    public static final String ARCHIVADA = "ARCHIVADA";

    /** El estado de la prueba que enseña la fila. */
    public static final String BORRADOR = "BORRADOR";
    /** El filtro «La rinde alguien»: la misma frontera que la vara del editor. */
    public static final String EN_USO = "EN_USO";

    /**
     * Una vacante con su versión escrita en el editor, tal como se pinta en la lista.
     *
     * @param sede               «San Isidro, Lima», «Arequipa» o «Remoto»: lo que separa dos
     *                           vacantes con el mismo título. Nula si no hay nada que decir
     * @param nivel              el del puesto; nulo sin puesto o sin nivel
     * @param situacion          SIN_PUBLICAR · PUBLICADA · CERRADA · ARCHIVADA
     * @param estado             PUBLICADA si tiene una publicada (es la que se enseña), o BORRADOR
     * @param laRindeAlguien     la vara del editor ya no se mueve: en las preguntas, la vacante
     *                           tiene postulantes; en la prueba, alguien ya abrió la suya
     * @param hayBorradorAbierto tiene una publicada y además un borrador abierto
     * @param entregables        solo en la prueba: todos, los de pregunta y los generales
     * @param modalidad          solo en la prueba: CRONOMETRADA · PLAZO_ABIERTO, o nula
     * @param minutos            solo en la cronometrada
     * @param dias               solo en las de plazo abierto de antes de la V68
     * @param publicadaEn        cuándo se publicó la versión; nula si nunca se publicó
     * @param puedeUsarEnOtra    con {@code editar_vacante} y una versión publicada
     */
    public record FilaDePrueba(Long vacanteId, String titulo, String sede, String nivel,
                               String situacion, String estado, boolean laRindeAlguien,
                               boolean hayBorradorAbierto, int criterios, int preguntas,
                               Integer entregables, String modalidad, Integer minutos,
                               Integer dias, Instant publicadaEn, boolean puedeUsarEnOtra) {
    }

    /**
     * Una vacante que podría recibir la copia. Las que no pueden van igual, con su motivo, para
     * que el panel las enseñe deshabilitadas.
     *
     * @param situacion     SIN_PUBLICAR · PUBLICADA (las cerradas y archivadas no se editan)
     * @param tieneBorrador ya hay un borrador que la copia reemplazaría entero
     * @param puedeRecibir  falso cuando ya la rinde alguien
     * @param motivo        por qué no puede recibirla, o nulo
     */
    public record VacanteDestino(Long vacanteId, String titulo, String sede, String nivel,
                                 String situacion, boolean tieneBorrador, boolean puedeRecibir,
                                 String motivo) {
    }
}
