package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Avanzar por el recorrido de la vacante y entrar a cada etapa (V95).
 *
 * <p>Toda entrada a una etapa pasa por aquí: «Avanzar» desde el panel, el pase automático, el
 * cierre de la Validación y el movimiento a mano. Así lo que pasa al entrar —crear la prueba,
 * abrir el periodo, mirar las sesiones— pasa igual venga de donde venga, y lo decide la pieza de
 * la etapa ({@link DefinicionDeEtapa}), no quien mueve.
 *
 * <p>La etapa siguiente sale del recorrido de la vacante, nunca del orden del catálogo: en una
 * vacante Perfil → Simulación → Prueba, tras el Perfil viene la Simulación.
 */
@Service
@RequiredArgsConstructor
public class AvanceDelRecorrido {

    private final MaquinaEstados maquina;
    private final EstadoPostulacionRepository estados;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final RecorridoDeLaVacante recorridos;
    private final RegistroDeEtapas registro;

    /**
     * Un paso ya decidido: el estado al que entra de verdad, el motivo que queda escrito y, si
     * cambia de etapa, la pieza de la etapa a la que entra.
     */
    public record Paso(String estado, String motivo, DefinicionDeEtapa etapa) {

        /** Lo que la etapa hace cuando la persona ya está dentro. Llamar tras la transición. */
        public void trasEntrar(Postulacion postulacion) {
            if (etapa != null) {
                etapa.trasEntrar(postulacion, estado);
            }
        }
    }

    /** El recorrido de la vacante, con los momentos de entrada de cada etapa. */
    public Recorrido recorridoDe(Long vacanteId) {
        return new Recorrido(recorridos.de(vacanteId), registro.entradas());
    }

    /** El estado siguiente de la postulación en el recorrido de su vacante, o vacío al final. */
    public Optional<EstadoPostulacion> siguiente(Postulacion postulacion) {
        return maquina.siguiente(postulacion.getEstadoCodigo(),
                recorridoDe(postulacion.getVacanteId()));
    }

    /**
     * Si la vacante tiene con qué llenar la etapa de ese estado. Lo pregunta el pase automático:
     * sin prueba publicada no se mueve a nadie a la prueba.
     */
    public boolean listaParaEntrar(EstadoPostulacion destino, Vacante vacante) {
        return registro.de(destino.getEtapaCodigo())
                .map(pieza -> pieza.listaParaEntrar(vacante))
                .orElse(true);
    }

    /**
     * Avanza la postulación al estado siguiente de su recorrido: entra (si cambia de etapa),
     * mueve y deja que la etapa haga lo suyo después.
     *
     * @return el estado al que entró, o vacío si no hay siguiente (no se mueve nada)
     */
    public Optional<String> avanzar(Postulacion postulacion, ContextoUsuario quien, String motivo,
                                    boolean esSistema) {
        Optional<EstadoPostulacion> siguiente = siguiente(postulacion);
        if (siguiente.isEmpty()) {
            return Optional.empty();
        }
        Paso paso = entrar(postulacion, siguiente.get().getCodigo(), motivo);
        maquina.transicionar(postulacion, paso.estado(), quien, paso.motivo(), esSistema, false, null);
        paso.trasEntrar(postulacion);
        return Optional.of(paso.estado());
    }

    /**
     * Lo que pasa ANTES de mover la postulación a {@code destino}: si cambia de etapa, la pieza de
     * esa etapa crea lo que la persona necesita y puede llevarla a otro paso. Quien llama mueve
     * con el {@link Paso} que recibe y después llama a {@link Paso#trasEntrar}.
     *
     * <p>Moverse dentro de la misma etapa no crea nada, y de un estado final o desconocido no se
     * sale: esa negativa la da la máquina de estados con su mensaje, sin crear nada antes.
     *
     * <p>⚠️ <b>No se entra a una etapa que la vacante no tiene</b> (409): por ahí saldría el aviso
     * de una etapa que la persona nunca iba a recorrer.
     *
     * <p><b>Todo o nada.</b> Corre en la transacción del llamador y antes de la transición: si
     * crear falla, no se guarda ni lo creado, ni la transición, ni sale el aviso.
     */
    public Paso entrar(Postulacion postulacion, String destino, String motivo) {
        EstadoPostulacion origen = postulacion.getEstadoCodigo() == null ? null
                : estados.findById(postulacion.getEstadoCodigo()).orElse(null);
        String etapaDestino = destino == null ? null
                : estados.findById(destino).map(EstadoPostulacion::getEtapaCodigo).orElse(null);
        if (origen == null || origen.isEsFinal() || etapaDestino == null
                || etapaDestino.equals(origen.getEtapaCodigo())) {
            return new Paso(destino, motivo, null);
        }

        if (!recorridos.tiene(postulacion.getVacanteId(), etapaDestino)) {
            throw new IllegalStateException("Esta vacante no tiene la etapa «" + etapaDestino
                    + "» en su recorrido: no se puede mover a nadie a ella");
        }
        DefinicionDeEtapa pieza = registro.de(etapaDestino).orElse(null);
        if (pieza == null) {
            return new Paso(destino, motivo, null);
        }
        if (pieza.bloqueaLaFila()) {
            exigirQueSigaDondeEstaba(postulacion);
        }
        DefinicionDeEtapa.Destino adonde = pieza.alEntrar(new DefinicionDeEtapa.Entrada(
                postulacion, laVacanteDe(postulacion), destino, motivo));
        return new Paso(adonde.estado(), adonde.motivo(), pieza);
    }

    /**
     * Que nadie la haya movido desde que se leyó, y que nadie la mueva hasta terminar.
     *
     * <p>Dos entradas a la vez —dos «Avanzar», o un «Avanzar» y un movimiento manual— leían el
     * mismo estado de partida. Si las dos creaban, la clave única paraba a una; pero si las dos
     * reutilizaban, las dos escribían su transición. Con la fila bloqueada, la segunda espera a la
     * primera, ve que el estado ya cambió y se planta sin escribir nada.
     */
    private void exigirQueSigaDondeEstaba(Postulacion postulacion) {
        String guardado = postulaciones.estadoBloqueandoLaFila(postulacion.getId());
        if (!Objects.equals(postulacion.getEstadoCodigo(), guardado)) {
            throw new IllegalStateException("Esta postulación acaba de moverse a «" + guardado
                    + "» desde otra pantalla: vuelve a cargarla antes de seguir");
        }
    }

    /**
     * La vacante de la postulación, por su organización: es por fuerza de la misma empresa, y
     * preguntarlo así no deja una búsqueda suelta por id.
     */
    private Vacante laVacanteDe(Postulacion postulacion) {
        return vacantes.findByIdAndOrganizacionId(postulacion.getVacanteId(),
                        postulacion.getOrganizacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante de esta postulación ya no existe"));
    }
}
