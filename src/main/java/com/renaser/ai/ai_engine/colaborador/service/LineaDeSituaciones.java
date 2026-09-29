package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.entity.SituacionDeAntes;
import com.renaser.ai.ai_engine.colaborador.entity.SituacionLaboral;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La línea de tiempo de un periodo: qué situación rige cada día.
 *
 * <p>Una sola regla para todo: las situaciones vivas (sin anular) se ordenan por su fecha
 * desde y, a igual fecha, por el orden en que se registraron. Cada una rige hasta el día antes
 * de la siguiente; la última, hasta el cese o sin fin. Dos del mismo día dejan a la primera
 * como un tramo vacío: sigue en el historial y nunca rige.
 */
public final class LineaDeSituaciones {

    private LineaDeSituaciones() {}

    public static final Comparator<SituacionLaboral> EN_ORDEN = Comparator
            .comparing(SituacionLaboral::getVigenteDesde)
            .thenComparing(SituacionLaboral::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    public static List<SituacionLaboral> vivas(List<SituacionLaboral> todas) {
        return todas.stream().filter(s -> !s.estaAnulada()).sorted(EN_ORDEN).toList();
    }

    /** Recalcula el «hasta» de cada viva. Devuelve las que cambiaron, para guardarlas. */
    public static List<SituacionLaboral> recalcular(List<SituacionLaboral> todas, LocalDate fechaCese) {
        List<SituacionLaboral> vivas = vivas(todas);
        List<SituacionLaboral> cambiadas = new java.util.ArrayList<>();
        for (int i = 0; i < vivas.size(); i++) {
            SituacionLaboral s = vivas.get(i);
            LocalDate hasta = i + 1 < vivas.size()
                    ? vivas.get(i + 1).getVigenteDesde().minusDays(1)
                    : fechaCese;
            if (!java.util.Objects.equals(hasta, s.getVigenteHasta())) {
                s.setVigenteHasta(hasta);
                cambiadas.add(s);
            }
        }
        return cambiadas;
    }

    /**
     * La que se enseña hoy: la última que ya empezó a regir; si todavía no entró, la primera.
     * Tras un cese, la última que rigió.
     */
    public static Optional<SituacionLaboral> vigente(List<SituacionLaboral> todas, LocalDate hoy) {
        List<SituacionLaboral> rigen = vivas(todas).stream().filter(s -> !s.esTramoVacio()).toList();
        Optional<SituacionLaboral> yaRige = rigen.stream()
                .filter(s -> !s.getVigenteDesde().isAfter(hoy))
                .reduce((primera, segunda) -> segunda);
        return yaRige.isPresent() ? yaRige : rigen.stream().findFirst();
    }

    /** Los cambios que todavía no rigen, sin contar la situación con la que se entra. */
    public static List<SituacionLaboral> programadas(List<SituacionLaboral> todas, LocalDate hoy) {
        Optional<SituacionLaboral> actual = vigente(todas, hoy);
        return vivas(todas).stream()
                .filter(s -> !s.esTramoVacio())
                .filter(s -> s.getVigenteDesde().isAfter(hoy))
                .filter(s -> actual.map(a -> !a.getId().equals(s.getId())).orElse(true))
                .toList();
    }

    /** La última del periodo: de la que parte un cambio nuevo o un reingreso. */
    public static Optional<SituacionLaboral> ultima(List<SituacionLaboral> todas) {
        List<SituacionLaboral> vivas = vivas(todas);
        return vivas.isEmpty() ? Optional.empty() : Optional.of(vivas.get(vivas.size() - 1));
    }

    // ============ Lo que no existe para quien no ve sueldos ============

    /**
     * La viva que rige justo antes de {@code s}, que puede estar anulada: contra ella se mide
     * qué cambió. Null si {@code s} es la primera.
     */
    public static SituacionLaboral anteriorViva(List<SituacionLaboral> todas, SituacionLaboral s) {
        SituacionLaboral anterior = null;
        for (SituacionLaboral v : vivas(todas)) {
            if (v.getId() != null && v.getId().equals(s.getId()) || EN_ORDEN.compare(v, s) > 0) {
                break;
            }
            anterior = v;
        }
        return anterior;
    }

    /**
     * Contra qué se mide {@code s}: qué cambió. Una viva, contra la viva que rige justo antes,
     * que es lo que cambia de verdad ese día. Una anulada ya no está en la línea, y medirla contra
     * la que hoy le queda delante le atribuiría lo que cambiaron otros después: se mide contra lo
     * que tenía detrás al anularse. Null si {@code s} es la primera.
     */
    public static SituacionLaboral frenteA(List<SituacionLaboral> todas, SituacionLaboral s) {
        if (s.estaAnulada() && s.getAntesAlAnular() != null) {
            return s.getAntesAlAnular().comoSituacion();
        }
        return anteriorViva(todas, s);
    }

    /**
     * Guarda en cada una de {@code porAnular} la viva que tiene detrás, antes de anular ninguna:
     * dos que se anulan juntas se miden como estaban. Hay que llamarlo antes de marcarlas.
     */
    public static void guardarLoDeAntes(List<SituacionLaboral> todas, List<SituacionLaboral> porAnular) {
        List<SituacionDeAntes> copias = porAnular.stream()
                .map(s -> SituacionDeAntes.de(anteriorViva(todas, s))).toList();
        for (int i = 0; i < porAnular.size(); i++) {
            porAnular.get(i).setAntesAlAnular(copias.get(i));
        }
    }

    /**
     * Si de {@code antes} a {@code despues} solo cambió el sueldo. Sin {@code ver_sueldos}, un
     * cambio así no existe: ni sale en el historial ni entre los programados, ni se anula.
     */
    public static boolean soloCambiaElSueldo(SituacionLaboral antes, SituacionLaboral despues) {
        return antes != null && mismoPuesto(antes, despues) && !mismoSueldo(antes, despues);
    }

    /**
     * {@link #soloCambiaElSueldo} contra lo que {@code s} tiene detrás ({@link #frenteA}): el
     * mismo criterio que el historial, también para una anulada.
     */
    public static boolean soloTocaElSueldo(List<SituacionLaboral> todas, SituacionLaboral s) {
        return soloCambiaElSueldo(frenteA(todas, s), s);
    }

    /**
     * La última del periodo para quien no ve sueldos: sin los ajustes de solo sueldo, que para él
     * no existen, rijan ya o todavía no. Tampoco le cierran las fechas de un cambio nuevo.
     */
    public static Optional<SituacionLaboral> ultimaSinSueldo(List<SituacionLaboral> todas) {
        return ultima(todas).map(u -> visibleSinSueldo(todas, u));
    }

    /**
     * Lo que ve en lugar de {@code s} quien no ve sueldos: el último cambio anterior o igual a
     * {@code s} que no fue de solo sueldo. Tiene el mismo puesto que {@code s} —lo único distinto
     * es el sueldo, que él no ve—, y su fecha y su motivo son los que le corresponden: los de un
     * ajuste que ya rige delatarían el ajuste.
     */
    public static SituacionLaboral visibleSinSueldo(List<SituacionLaboral> todas, SituacionLaboral s) {
        List<SituacionLaboral> vivas = vivas(todas);
        int i = posicion(vivas, s);
        while (i > 0 && soloCambiaElSueldo(vivas.get(i - 1), vivas.get(i))) {
            i--;
        }
        return i < 0 ? s : vivas.get(i);
    }

    /**
     * Hasta cuándo rige {@code s} para quien no ve sueldos: el día antes del siguiente cambio que
     * sí ve, o el cese. El «hasta» guardado terminaría el día antes de un ajuste que no existe
     * para él.
     */
    public static LocalDate hastaSinSueldo(List<SituacionLaboral> todas, SituacionLaboral s, LocalDate fechaCese) {
        List<SituacionLaboral> vivas = vivas(todas);
        int i = posicion(vivas, s);
        if (i < 0) {
            return s.getVigenteHasta();
        }
        for (int j = i + 1; j < vivas.size(); j++) {
            if (!soloCambiaElSueldo(vivas.get(j - 1), vivas.get(j))) {
                return vivas.get(j).getVigenteDesde().minusDays(1);
            }
        }
        return fechaCese;
    }

    private static int posicion(List<SituacionLaboral> vivas, SituacionLaboral s) {
        for (int i = 0; i < vivas.size(); i++) {
            SituacionLaboral v = vivas.get(i);
            if (v == s || v.getId() != null && v.getId().equals(s.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * La viva de la que parte un cambio que rige desde {@code dia}: la última que empieza ese día o
     * antes. Si empieza el mismo día, el cambio la sustituye y hereda lo que no toca.
     */
    public static Optional<SituacionLaboral> deLaQueParte(List<SituacionLaboral> todas, LocalDate dia) {
        return vivas(todas).stream().filter(s -> !s.getVigenteDesde().isAfter(dia)).reduce((a, b) -> b);
    }

    /** Copia en {@code destino} todo lo de {@code origen} salvo el sueldo y la moneda. */
    public static void copiarPuesto(SituacionLaboral origen, SituacionLaboral destino) {
        destino.setSedeId(origen.getSedeId());
        destino.setAreaId(origen.getAreaId());
        destino.setPuestoId(origen.getPuestoId());
        destino.setJefeColaboradorId(origen.getJefeColaboradorId());
        destino.setTipoContrato(origen.getTipoContrato());
        destino.setFinContrato(origen.getFinContrato());
        destino.setFinPeriodoPrueba(origen.getFinPeriodoPrueba());
        destino.setRegimenLaboral(origen.getRegimenLaboral());
    }

    /** Todo lo de la situación salvo el sueldo: dónde, con quién y con qué contrato. */
    static boolean mismoPuesto(SituacionLaboral a, SituacionLaboral b) {
        return Objects.equals(a.getSedeId(), b.getSedeId())
                && Objects.equals(a.getAreaId(), b.getAreaId())
                && Objects.equals(a.getPuestoId(), b.getPuestoId())
                && Objects.equals(a.getJefeColaboradorId(), b.getJefeColaboradorId())
                && Objects.equals(a.getTipoContrato(), b.getTipoContrato())
                && Objects.equals(a.getFinContrato(), b.getFinContrato())
                && Objects.equals(a.getFinPeriodoPrueba(), b.getFinPeriodoPrueba())
                && Objects.equals(a.getRegimenLaboral(), b.getRegimenLaboral());
    }

    static boolean mismoSueldo(SituacionLaboral a, SituacionLaboral b) {
        if (a.getSueldoBase() == null || b.getSueldoBase() == null) {
            return a.getSueldoBase() == null && b.getSueldoBase() == null;
        }
        return a.getSueldoBase().compareTo(b.getSueldoBase()) == 0 && Objects.equals(a.getMoneda(), b.getMoneda());
    }
}
