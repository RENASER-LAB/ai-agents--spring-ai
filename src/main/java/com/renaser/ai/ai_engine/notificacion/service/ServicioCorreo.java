package com.renaser.ai.ai_engine.notificacion.service;

import com.renaser.ai.ai_engine.notificacion.entity.CorreoEnviado;
import com.renaser.ai.ai_engine.notificacion.entity.PlantillaCorreo;
import com.renaser.ai.ai_engine.notificacion.repository.CorreoEnviadoRepository;
import com.renaser.ai.ai_engine.notificacion.repository.PlantillaCorreoRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Plantilla activa -> reemplazo de {{variables}} -> fila en correo_enviado con el texto
// EXACTO que salió -> transporte. Si un candidato reclama meses después, se lee lo que
// se le dijo, no lo que la plantilla dice hoy.
@Service
@Slf4j
public class ServicioCorreo {

    /**
     * Los correos de la cuenta, que salen siempre (V92): crearla, recuperar la clave, el borrado
     * de datos, las invitaciones del equipo y el aviso del tope de la IA.
     *
     * <p>Todos los demás son de sus procesos —el cambio de etapa (también el texto propio que una
     * vacante eligió para él), la postulación recibida, la simulación, los recordatorios y «más
     * tiempo»— y no salen si la persona apagó el correo en «Notificaciones». Va como lista de los
     * que salen siempre, y no de los que se apagan, porque una vacante puede elegir su propio
     * texto con otro código: un aviso de etapa no puede escaparse del interruptor por llamarse
     * distinto.
     */
    public static final Set<String> DE_LA_CUENTA = Set.of(
            "CUENTA_CREADA", "RECUPERAR_CLAVE_CANDIDATO", "RECUPERAR_CLAVE_EQUIPO",
            "BORRADO_DATOS", "BORRADO_EJECUTADO", "INVITACION_EQUIPO", "TOPE_IA_AVISO");

    private final PlantillaCorreoRepository plantillas;
    private final CorreoEnviadoRepository enviados;
    private final EnviadorCorreo transporte;
    /** Si apagó los correos de sus procesos (V92). Nulo en las pruebas que no lo miran: todo sale. */
    private final PreferenciasDeAviso preferencias;

    /** Sin preferencias: todo sale. Lo usan las pruebas del registro y del transporte. */
    public ServicioCorreo(PlantillaCorreoRepository plantillas, CorreoEnviadoRepository enviados,
                          EnviadorCorreo transporte) {
        this(plantillas, enviados, transporte, null);
    }

    @Autowired
    public ServicioCorreo(PlantillaCorreoRepository plantillas, CorreoEnviadoRepository enviados,
                          EnviadorCorreo transporte, PreferenciasDeAviso preferencias) {
        this.plantillas = plantillas;
        this.enviados = enviados;
        this.transporte = transporte;
        this.preferencias = preferencias;
    }

    public void enviar(Long organizacionId, Long usuarioId, String correoDestino,
                       String codigoPlantilla, Map<String, String> variables) {
        if (apagado(usuarioId, codigoPlantilla)) {
            return;
        }
        var plantilla = plantillas
                .findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(organizacionId, codigoPlantilla)
                .orElse(null);
        if (plantilla == null) {
            // Que falte una plantilla no puede frenar una postulación. Se anota y se sigue.
            log.error("No hay plantilla activa «{}» para la organización {}", codigoPlantilla, organizacionId);
            return;
        }
        enviarCon(plantilla, usuarioId, correoDestino, variables);
    }

    /**
     * La plantilla activa de la organización o, si a ella le falta, la de la organización
     * de recambio —la plataforma—.
     *
     * <p>Existe para los correos que no pueden perderse en silencio. {@link #enviar} omite
     * el envío cuando falta la plantilla, y eso vale para un aviso de postulación; no para el
     * enlace de una contraseña nueva, donde la pantalla ya le dijo a la persona que revise su
     * correo. Las empresas dadas de alta antes de que existiera una plantilla no la tienen
     * (el alta copia las de la plataforma una sola vez), y la de la plataforma dice lo mismo.
     *
     * <p>Vacío solo si no la tiene ninguna de las dos; quien llama decide qué hacer, y lo
     * que no puede hacer es prometer un correo que no va a salir.
     */
    public Optional<PlantillaCorreo> plantillaConRecambio(Long organizacionId, Long recambioId,
                                                          String codigoPlantilla) {
        return plantillas
                .findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(organizacionId, codigoPlantilla)
                .or(() -> recambioId == null || recambioId.equals(organizacionId)
                        ? Optional.empty()
                        : plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                                recambioId, codigoPlantilla));
    }

    /**
     * Arma y envía con una plantilla ya elegida: la fila de {@code correo_enviado} con el
     * texto exacto, y después el transporte. Devuelve cómo acabó el intento, que es lo
     * mismo que queda escrito en {@code estado_entrega}.
     */
    public EnviadorCorreo.Resultado enviarCon(PlantillaCorreo plantilla, Long usuarioId,
                                              String correoDestino, Map<String, String> variables) {
        if (apagado(usuarioId, plantilla.getCodigo())) {
            // Ni fila en correo_enviado: no se le dijo nada, y el registro no puede decir otra cosa.
            return EnviadorCorreo.Resultado.NO_ENVIADO;
        }
        String asunto = reemplazar(plantilla.getAsunto(), variables);
        String cuerpo = reemplazar(plantilla.getCuerpo(), variables);

        CorreoEnviado registro = enviados.save(CorreoEnviado.builder()
                .usuarioId(usuarioId)
                .plantillaCorreoCodigo(plantilla.getCodigo())
                .versionPlantilla(plantilla.getVersion())
                .asunto(asunto)
                .cuerpo(cuerpo)
                .canal("CORREO")
                .enviadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());

        // El registro se guarda ANTES de intentar el envio: lo que se le dijo al candidato
        // queda escrito aunque el servidor de correo se caiga a mitad. Despues se anota como
        // acabo el intento, porque una fila que dice «enviado» cuando nadie lo recibio es
        // peor que no tener la fila: se descubre cuando el candidato reclama.
        EnviadorCorreo.Resultado resultado = correoDestino == null
                ? EnviadorCorreo.Resultado.NO_ENVIADO
                : transporte.enviar(correoDestino, asunto, cuerpo);

        registro.setEstadoEntrega(resultado.name());
        enviados.save(registro);
        return resultado;
    }

    /**
     * Un correo de sus procesos a quien apagó el correo en «Notificaciones» (V92). Los de la
     * cuenta ({@link #DE_LA_CUENTA}) nunca; y si no se puede saber, sale: perder un aviso en
     * silencio es peor que mandar uno de más.
     */
    private boolean apagado(Long usuarioId, String codigoPlantilla) {
        if (preferencias == null || usuarioId == null || DE_LA_CUENTA.contains(codigoPlantilla)) {
            return false;
        }
        try {
            if (preferencias.recibeCorreo(usuarioId)) {
                return false;
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo leer si el usuario {} recibe correos; sale igual: {}", usuarioId,
                    e.getMessage());
            return false;
        }
        log.info("El correo «{}» no sale: el usuario {} apagó los correos de sus procesos",
                codigoPlantilla, usuarioId);
        return true;
    }

    private String reemplazar(String texto, Map<String, String> variables) {
        String resultado = texto;
        for (var v : variables.entrySet()) {
            resultado = resultado.replace("{{" + v.getKey() + "}}", v.getValue() == null ? "" : v.getValue());
        }
        return resultado;
    }
}
