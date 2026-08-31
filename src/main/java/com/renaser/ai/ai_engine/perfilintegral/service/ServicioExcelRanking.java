package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * El ranking de una vacante, volcado a una hoja de cálculo.
 *
 * <p><b>Por qué existe.</b> La decisión de a quién se pasa a la siguiente etapa se toma
 * comparando candidatos, y compararlos de uno en uno en la ficha no deja compararlos. Esto
 * saca lo mismo que hay en pantalla, en dos hojas que se pueden ordenar, filtrar y mandar a
 * quien decide y no entra al panel. Es el mismo trabajo que hacía a mano el script
 * {@code scripts/excel-de-la-prueba.py}, con la diferencia de que aquí no hace falta un
 * portátil con Python para que el equipo se lleve su tanda.
 *
 * <p><b>No ordena nada.</b> Filtrar y ordenar ocurre en el cliente; aquí llega la lista ya
 * ordenada y estas filas se escriben en ese orden. Reordenar por nota «para ayudar» sería
 * devolver una hoja que no se parece a la pantalla desde la que se pidió.
 *
 * <p><b>No escribe en la base.</b> Ni una nota, ni un estado, ni un rastro en la postulación.
 */
public interface ServicioExcelRanking {

    /**
     * Las dos hojas —Resumen y Detalle— de la etapa pedida, en el orden pedido.
     *
     * <p>Solo hay columnas escritas para {@code PERFIL_INTEGRAL} y {@code PRUEBA_PUESTO}:
     * las demás etapas puntúan con otros instrumentos y volcarlas con estas cabeceras
     * enseñaría una hoja llena de huecos que parecerían datos que faltan.
     *
     * <p>Las postulaciones que no son de esta vacante <b>se quedan fuera y la hoja lo dice</b>
     * al pie. Es lo que pasa cuando el cliente arrastra una selección vieja; tirar el volcado
     * entero por un id rancio sería peor que volcar los que sí valen y avisar.
     *
     * @throws IllegalArgumentException si la etapa no es una de las dos, o si ninguna de las
     *                                  postulaciones pedidas es de esta vacante — una hoja
     *                                  vacía se lee como «no hay candidatos», que es falso
     */
    ExcelDeRanking generar(ContextoUsuario quien, Long vacanteId, PedidoExcelRanking pedido);
}
