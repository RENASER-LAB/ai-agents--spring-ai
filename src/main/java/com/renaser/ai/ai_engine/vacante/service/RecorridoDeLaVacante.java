package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.pesos.entity.Etapa;
import com.renaser.ai.ai_engine.pesos.repository.EtapaRepository;
import com.renaser.ai.ai_engine.vacante.entity.VacanteEtapa;
import com.renaser.ai.ai_engine.vacante.repository.VacanteEtapaRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El recorrido de una vacante: sus etapas, en orden (V95).
 *
 * <p>Es la única fuente de «qué etapa viene después» y de «esta vacante tiene esta etapa»: la
 * máquina de estados, el pase automático, la decisión, el portal, WhatsApp, el Excel y los informes
 * preguntan aquí. Ver specs/Ready/etapas-por-vacante/spec.md.
 *
 * <p><b>Una vacante sin filas recorre el catálogo entero</b>, en el orden de {@code etapa.orden}:
 * es lo que hacían todas antes de la V95, y la migración se lo dio a todas las que existían. Solo
 * queda sin filas una vacante escrita por fuera del servicio —un guion o una prueba que inserte la
 * fila a mano—, y para ella lo prudente es lo de siempre.
 */
@Service
@RequiredArgsConstructor
public class RecorridoDeLaVacante {

    private final VacanteEtapaRepository recorridos;
    private final EtapaRepository etapas;

    /** El catálogo de etapas, en el orden de {@code etapa.orden}. */
    public List<Etapa> catalogo() {
        return etapas.findAllByOrderByOrdenAsc();
    }

    /** Los códigos del recorrido de una vacante, en su orden. */
    public List<String> de(Long vacanteId) {
        if (vacanteId == null) {
            return deSiempre(catalogo());
        }
        List<String> suyas = recorridos.findByVacanteIdOrderByOrdenAsc(vacanteId).stream()
                .map(VacanteEtapa::getEtapaCodigo)
                .toList();
        return suyas.isEmpty() ? deSiempre(catalogo()) : suyas;
    }

    /** Los recorridos de varias vacantes con una consulta, para las listas. */
    public Map<Long, List<String>> de(Collection<Long> vacanteIds) {
        Map<Long, List<String>> porVacante = new LinkedHashMap<>();
        if (vacanteIds == null || vacanteIds.isEmpty()) {
            return porVacante;
        }
        for (VacanteEtapa fila : recorridos.findByVacanteIdInOrderByVacanteIdAscOrdenAsc(vacanteIds)) {
            porVacante.computeIfAbsent(fila.getVacanteId(), id -> new ArrayList<>())
                    .add(fila.getEtapaCodigo());
        }
        List<String> deSiempre = null;
        for (Long id : vacanteIds) {
            if (!porVacante.containsKey(id)) {
                if (deSiempre == null) {
                    deSiempre = deSiempre(catalogo());
                }
                porVacante.put(id, deSiempre);
            }
        }
        return porVacante;
    }

    /** Si el recorrido de la vacante tiene esa etapa. */
    public boolean tiene(Long vacanteId, String etapaCodigo) {
        return de(vacanteId).contains(etapaCodigo);
    }

    /** El recorrido con el que nace una vacante: las etapas {@code en_predeterminado}, en orden. */
    public List<String> predeterminado() {
        return catalogo().stream()
                .filter(Etapa::isEnPredeterminado)
                .map(Etapa::getCodigo)
                .toList();
    }

    /**
     * Le da a una vacante recién creada el recorrido predeterminado. Solo inserta: una vacante
     * nueva no tiene filas que borrar.
     */
    public void ponerPredeterminado(Long vacanteId) {
        insertar(vacanteId, predeterminado());
    }

    /**
     * Escribe el recorrido de una vacante, sustituyendo el que tuviera. No valida: quien llama ya
     * pasó la lista por {@link #queFalla}.
     *
     * <p>⚠️ El borrado vacía la caché de la transacción (ver
     * {@link VacanteEtapaRepository#borrarElRecorrido}): lo que quien llama quiera cambiar en otras
     * entidades, que lo haga antes.
     */
    public void escribir(Long vacanteId, List<String> codigos) {
        recorridos.borrarElRecorrido(vacanteId);
        insertar(vacanteId, codigos);
    }

    private void insertar(Long vacanteId, List<String> codigos) {
        Instant ahora = Instant.now();
        List<VacanteEtapa> filas = new ArrayList<>();
        for (int i = 0; i < codigos.size(); i++) {
            filas.add(VacanteEtapa.builder()
                    .vacanteId(vacanteId)
                    .etapaCodigo(codigos.get(i))
                    .orden(i + 1)
                    .creadoEn(ahora)
                    .build());
        }
        recorridos.saveAll(filas);
    }

    /**
     * Qué falla en un recorrido pedido, dicho para quien lo pidió, o {@code null} si vale.
     *
     * <p>Vale cualquier lista que empiece por la etapa fija, sin repetidas y con códigos del
     * catálogo. El Perfil Integral solo también vale. No hay reglas de orden entre las demás.
     */
    public static String queFalla(List<String> pedido, List<Etapa> catalogo) {
        if (pedido == null || pedido.isEmpty()) {
            return "El recorrido está vacío: tiene que empezar, como mínimo, por "
                    + nombreDeLaFija(catalogo) + ".";
        }
        Map<String, Etapa> porCodigo = new LinkedHashMap<>();
        catalogo.forEach(e -> porCodigo.put(e.getCodigo(), e));
        List<String> desconocidas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        List<String> repetidas = new ArrayList<>();
        for (String codigo : pedido) {
            if (codigo == null || !porCodigo.containsKey(codigo)) {
                desconocidas.add(String.valueOf(codigo));
            } else if (!vistas.add(codigo)) {
                repetidas.add(codigo);
            }
        }
        if (!desconocidas.isEmpty()) {
            return "Estas etapas no existen: " + String.join(", ", desconocidas)
                    + ". Las que hay: " + String.join(", ", porCodigo.keySet()) + ".";
        }
        if (!repetidas.isEmpty()) {
            return "Cada etapa va una sola vez en el recorrido, y se repite: "
                    + String.join(", ", repetidas) + ".";
        }
        for (Etapa etapa : catalogo) {
            if (etapa.isFijaPrimera() && !etapa.getCodigo().equals(pedido.get(0))) {
                return "El recorrido tiene que empezar por " + etapa.getNombre()
                        + ": va siempre primera.";
            }
        }
        return null;
    }

    private static String nombreDeLaFija(List<Etapa> catalogo) {
        return catalogo.stream().filter(Etapa::isFijaPrimera).map(Etapa::getNombre)
                .findFirst().orElse("la primera etapa");
    }

    private static List<String> deSiempre(List<Etapa> catalogo) {
        return catalogo.stream().map(Etapa::getCodigo).toList();
    }
}
