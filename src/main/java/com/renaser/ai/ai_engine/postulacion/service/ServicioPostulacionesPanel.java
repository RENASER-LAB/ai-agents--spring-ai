package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

public interface ServicioPostulacionesPanel {

    // La bandeja: todo lo que espera a alguien, filtrado por el alcance de quien mira
    List<FilaBandeja> bandeja(ContextoUsuario quien, String esperaA);

    ConteoEmbudo embudo(ContextoUsuario quien, Long vacanteId);

    FichaPostulacion ficha(ContextoUsuario quien, Long postulacionId);

    List<PasoHistorial> historial(ContextoUsuario quien, Long postulacionId);

    // Una persona puede mover una postulación a donde quiera, siempre con motivo
    void transicionar(ContextoUsuario quien, Long postulacionId, Transicionar datos);

    // Aplica el estado siguiente calculado por la máquina
    void confirmarAvance(ContextoUsuario quien, Long postulacionId, String motivo);

    /**
     * Corrige el contacto de la ficha que saco la IA del curriculum.
     *
     * <p>Existe porque no habia ninguna otra forma: el unico sitio del codigo que escribe
     * `dato_cv` es el propio agente, asi que un correo mal leido solo se podia arreglar
     * entrando a la base a mano. Y un correo mal leido deja al candidato fuera del proceso
     * sin que nadie lo note: el aviso se registra como NO_ENVIADO y el sistema lo da por
     * avisado.
     *
     * @return el contacto como queda despues
     */
    ContactoDelCandidato corregirContacto(ContextoUsuario quien, Long postulacionId,
                                          CorregirContacto datos);

    /**
     * El enlace para que el candidato de esta postulación entre al portal sin contraseña.
     *
     * <p>Pasa por el mismo guardián que el resto de escrituras sobre una postulación: que sea
     * de esta empresa, que el alcance de {@code mover_postulacion} llegue a ella y que su
     * vacante siga existiendo —una eliminada contesta 404 (V60)—. Antes el controlador
     * llamaba directo al generador, que busca la postulación por id suelto y no preguntaba
     * ninguna de las tres cosas.
     */
    ServicioEnlaceAcceso.EnlaceGenerado enlaceDeAcceso(ContextoUsuario quien, Long postulacionId);

    byte[] descargarArchivo(ContextoUsuario quien, Long archivoId, StringBuilder nombreSalida);

    /**
     * Un enlace temporal para bajarse el archivo <b>del almacen directamente</b>.
     *
     * <p>Es lo que evita que un curriculum de diez megas entre y salga del backend solo para
     * llegar a un navegador: paga el doble de trafico, ocupa memoria mientras dura, y varios
     * a la vez se notan. Con el enlace, el navegador habla con el almacen y aqui no pasa
     * nada.
     *
     * <p>El permiso se comprueba <b>antes</b> de firmar. Despues ya no hay a quien
     * preguntarle: el enlace vale por si solo, y por eso dura minutos y no horas.
     *
     * @throws IllegalStateException si el almacen no sabe firmar enlaces —el de disco no—,
     *                               y entonces toca {@link #descargarArchivo}
     */
    EnlaceArchivo enlaceDeArchivo(ContextoUsuario quien, Long archivoId);

    /**
     * El mismo enlace, pero el que se escribe DENTRO del Excel del ranking.
     *
     * <p>Se diferencia en una sola cosa —dura horas en vez de minutos— y existe como metodo
     * aparte, y no como un parametro del de arriba, justamente para que esa diferencia
     * tenga que pedirse por su nombre. El porque y lo que cuesta estan en
     * {@code AlmacenArchivos.urlDeVolcado}.
     *
     * <p>Comprueba el MISMO permiso que la descarga: son dos formas de entregar lo mismo, y
     * la que no comprobara nada seria la puerta de atras.
     *
     * @return vacio si este almacen no sabe firmar. Aqui devuelve vacio en vez de reventar
     *         porque quien llama vuelca ochenta filas y una excepcion por una dejaria sin
     *         archivo a las ochenta. ⚠️ El almacen de Supabase, que es el unico que hay hoy,
     *         nunca devuelve vacio: o firma o lanza. Quien llame tiene que atender los dos
     *         caminos igual, porque el que sobra hoy es el que queda cuando aparezca otro
     */
    java.util.Optional<EnlaceArchivo> enlaceDeVolcado(ContextoUsuario quien, Long archivoId);
}
