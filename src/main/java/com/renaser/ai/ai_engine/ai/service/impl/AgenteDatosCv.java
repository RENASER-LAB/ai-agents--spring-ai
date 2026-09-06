package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saca los datos del candidato del currículum: quién es, cómo se le escribe, cuánto lleva
 * trabajando. <b>No puntúa nada.</b>
 *
 * <p>Existe porque el panel enseñaba notas y explicaciones pero ningún dato de la persona, y
 * para saber a quién llamar hacía falta abrir el PDF. Con esto la tanda se ve entera.
 *
 * <p><b>Nunca razona.</b> Copiar un nombre de un texto no exige deliberar, y es el
 * razonamiento lo que cuesta los segundos: en los agentes que sí puntúan son cuatro de cada
 * cinco tokens que el modelo escribe. Por eso este agente tarda un puñado de segundos y los
 * otros decenas.
 *
 * <p>Lee la misma versión recortada del currículum que los demás, así que la edad, el sexo y
 * el estado civil no le llegan aunque estuvieran en el archivo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteDatosCv implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "DATOS_CV";

    private static final String OBJETIVO = "Sacar los datos del candidato del currículum";

    // Compacto a propósito: lo que el modelo escribe es lo que cuesta el tiempo. Ni
    // explicaciones ni justificaciones, que aquí no aportan nada y triplicarían la espera.
    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "nombre": "<nombre completo, o null>",
              "email": "<correo, o null>",
              "telefono": "<telefono, o null>",
              "perfilResumen": "<una sola oracion sobre su perfil profesional>",
              "habilidades": ["<hasta cinco, las mas relevantes para el puesto>"],
              "experienciaMesesTotal": <numero de meses, o null si no se puede calcular>,
              "ultimoPuesto": "<el puesto mas reciente, o null>",
              "ultimaEmpresa": "<la empresa mas reciente, o null>",
              "ultimaMesesDuracion": <cuantos meses duro, o null>,
              "educacionMaxima": "<el nivel mas alto alcanzado, o null>",
              "experiencia": [{"puesto": "<puesto>", "empresa": "<empresa>",
                "desde": "<AAAA-MM>", "hasta": "<AAAA-MM, o null si sigue ahi>",
                "descripcion": "<una oracion, o null>"}],
              "educacion": [{"titulo": "<titulo o estudio>", "institucion": "<institucion>",
                "nivel": "<SECUNDARIA|TECNICA|UNIVERSITARIA|TITULADO|MAESTRIA|DOCTORADO, o null>",
                "desde": "<AAAA-MM, o null>", "hasta": "<AAAA-MM, o null>"}],
              "idiomas": [{"idioma": "<idioma>", "nivel": "<A1|A2|B1|B2|C1|C2|NATIVO>"}],
              "certificaciones": [{"nombre": "<nombre>", "entidad": "<entidad, o null>",
                "emitidaEn": "<AAAA-MM, o null>", "venceEn": "<AAAA-MM, o null>"}]
            }
            La experiencia del mas reciente al mas antiguo; si el curriculum no da la fecha
            de inicio de un empleo, omite ese empleo. Si da el año sin mes, usa enero.
            Los idiomas solo si el curriculum los declara: «basico» es A2, «intermedio» B1,
            «avanzado» C1; sin nivel declarado, omite el idioma.
            Si un dato no esta en el curriculum, pon null. No lo deduzcas y no lo inventes.
            Las listas pueden ir vacias. No agregues ningun campo mas, ni explicaciones,
            ni comentarios.
            """;

    /**
     * De dónde viene el currículum cuando no viene de una postulación.
     *
     * <p>Es el valor de {@code trabajo_ia.referencia_tabla} para la otra mitad de este
     * agente. Ver {@link #ejecutar}.
     */
    public static final String DEL_PERFIL = "lectura_cv_perfil";

    private final PuenteCalificacionIa puente;
    private final com.renaser.ai.ai_engine.perfil.service.PuenteLecturaCvPerfil puenteDelPerfil;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    /**
     * El mismo trabajo sobre dos orígenes distintos.
     *
     * <p><b>Un currículum llega por dos puertas</b>: pegado a una postulación, como siempre,
     * o subido por el candidato a su propio perfil, que no tiene postulación detrás y puede
     * que nunca la tenga. Lo que se le pide al modelo es <b>idéntico</b> —el mismo objetivo,
     * el mismo formato, la misma instrucción activa—, así que son dos puentes y no dos
     * agentes: un agente nuevo obligaría a sembrar su fila en {@code agente} y su
     * {@code instruccion_ia}, para acabar mandando exactamente el mismo prompt.
     *
     * <p>Se distingue por {@code referencia_tabla}, igual que hace el REDACTOR con
     * {@code "vacante"}. La columna {@code postulacion_id} de {@code trabajo_ia} ya admite
     * vacío desde la V11: esto no es un hueco que se abre, es uno que ya estaba.
     */
    @Override
    public void ejecutar(TrabajoIa trabajo) {
        if (DEL_PERFIL.equals(trabajo.getReferenciaTabla())) {
            ejecutarDelPerfil(trabajo);
            return;
        }

        InsumoDatos insumo = puente.insumoDatos(trabajo.getPostulacionId());
        log.info("DATOS_CV lee el currículum de la postulación {} ({} caracteres)",
                trabajo.getPostulacionId(), insumo.curriculum().length());

        // false: este agente nunca razona, sea cual sea la pasada. No hay nada que deliberar.
        EjecutorAgenteIa.Ejecutado<ResultadoDatos> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoDatos.class, false);
        puente.guardarDatos(trabajo.getPostulacionId(), salida.ejecucionIaId(), salida.resultado());
    }

    /** El currículum que el candidato subió a su perfil. Ver {@link #ejecutar}. */
    private void ejecutarDelPerfil(TrabajoIa trabajo) {
        Long lecturaId = trabajo.getReferenciaId();
        InsumoDatos insumo = puenteDelPerfil.insumo(lecturaId);
        log.info("DATOS_CV lee el currículum del perfil (lectura {}, {} caracteres)",
                lecturaId, insumo.curriculum().length());

        EjecutorAgenteIa.Ejecutado<ResultadoDatos> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoDatos.class, false);
        puenteDelPerfil.guardar(lecturaId, salida.ejecucionIaId(), salida.resultado());
    }
}
