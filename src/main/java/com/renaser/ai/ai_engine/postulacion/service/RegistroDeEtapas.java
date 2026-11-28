package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Las piezas que cumplen {@link DefinicionDeEtapa}, por código (V95).
 *
 * <p><b>Una etapa del catálogo sin su pieza no deja arrancar la aplicación</b>, con un mensaje que
 * la nombra: una vacante podría tenerla en su recorrido y la persona entraría a una etapa en la
 * que nada se crea ni se califica. Se comprueba al terminar de crear todos los beans, contra la
 * tabla {@code etapa} ya migrada.
 *
 * <p>Las piezas se piden a Spring perezosamente ({@link ObjectProvider}) y no en el constructor: la
 * de la Validación usa el servicio de la Validación, que a su vez avanza por el recorrido, y
 * pedirlas antes cerraría un círculo de dependencias.
 */
@Component
public class RegistroDeEtapas implements SmartInitializingSingleton {

    private final ObjectProvider<DefinicionDeEtapa> piezas;
    private final EtapaRepository etapas;
    private volatile Map<String, DefinicionDeEtapa> porCodigo;

    public RegistroDeEtapas(ObjectProvider<DefinicionDeEtapa> piezas, EtapaRepository etapas) {
        this.piezas = piezas;
        this.etapas = etapas;
    }

    /** Para las pruebas: un registro con estas piezas, sin Spring. */
    public static RegistroDeEtapas con(Collection<DefinicionDeEtapa> definiciones) {
        RegistroDeEtapas registro = new RegistroDeEtapas(null, null);
        registro.porCodigo = indexar(definiciones);
        return registro;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, DefinicionDeEtapa> indexadas = todas();
        List<String> sinPieza = new ArrayList<>();
        for (Etapa etapa : etapas.findAllByOrderByOrdenAsc()) {
            if (!indexadas.containsKey(etapa.getCodigo())) {
                sinPieza.add(etapa.getCodigo());
            }
        }
        if (!sinPieza.isEmpty()) {
            throw new IllegalStateException(faltan(sinPieza));
        }
    }

    /** El mensaje con el que no arranca: nombra cada etapa sin su pieza. */
    static String faltan(List<String> sinPieza) {
        return "Estas etapas del catálogo no tienen pieza que cumpla DefinicionDeEtapa: "
                + String.join(", ", sinPieza) + ". Una vacante podría tenerlas en su recorrido "
                + "y nadie sabría qué hacer al entrar. Programa su pieza o quítala del catálogo "
                + "(ver «Cómo agregar una etapa»).";
    }

    /** La pieza de una etapa, si la hay. */
    public Optional<DefinicionDeEtapa> de(String codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(todas().get(codigo));
    }

    /** El momento de entrada de las etapas que no entran por el primero. */
    public Map<String, String> entradas() {
        Map<String, String> entradas = new LinkedHashMap<>();
        todas().forEach((codigo, pieza) ->
                pieza.momentoDeEntrada().ifPresent(momento -> entradas.put(codigo, momento)));
        return entradas;
    }

    private Map<String, DefinicionDeEtapa> todas() {
        Map<String, DefinicionDeEtapa> hechas = porCodigo;
        if (hechas == null) {
            hechas = indexar(piezas.orderedStream().toList());
            porCodigo = hechas;
        }
        return hechas;
    }

    private static Map<String, DefinicionDeEtapa> indexar(Collection<DefinicionDeEtapa> definiciones) {
        Map<String, DefinicionDeEtapa> indexadas = new LinkedHashMap<>();
        for (DefinicionDeEtapa pieza : definiciones) {
            DefinicionDeEtapa otra = indexadas.putIfAbsent(pieza.codigo(), pieza);
            if (otra != null) {
                throw new IllegalStateException("La etapa " + pieza.codigo() + " tiene dos piezas que "
                        + "cumplen DefinicionDeEtapa: " + otra.getClass().getSimpleName() + " y "
                        + pieza.getClass().getSimpleName() + ". Tiene que haber una sola.");
            }
        }
        return Map.copyOf(indexadas);
    }
}
