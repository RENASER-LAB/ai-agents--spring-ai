package com.renaser.ai.ai_engine.seguridad.dto;

// El «solo lo suyo» convertido en algo que una query puede usar. @PreAuthorize decide
// si se puede llamar; esto decide QUÉ FILAS se ven. Un permiso con alcance no es un
// sí o un no: es un WHERE.
public record FiltroAlcance(Tipo tipo, Long usuarioId) {

    public enum Tipo { TODO, SUS_VACANTES, PROPIO }

    public static FiltroAlcance desde(String alcance, Long usuarioId) {
        return new FiltroAlcance(Tipo.valueOf(alcance), usuarioId);
    }

    /**
     * Para las queries de bandeja: null significa «no filtres por responsable».
     *
     * <p>⚠️ Solo distingue {@code SUS_VACANTES}: devuelve null tanto con {@code TODO} como con
     * {@code PROPIO}, y esos dos no significan lo mismo. Quien lo use tiene que tratar
     * {@code PROPIO} aparte —mira {@link #noAlcanzaANadieEnElPanel()}—, o enseñará la lista
     * entera a quien solo debería ver lo suyo. Mientras el reparto se cambiaba a mano en la
     * base esto no era alcanzable; desde que los permisos se editan por el panel, basta un PUT.
     */
    public Long responsableOFiltroNulo() {
        return tipo == Tipo.SUS_VACANTES ? usuarioId : null;
    }

    /**
     * Si este alcance no llega a ninguna fila del panel.
     *
     * <p>{@code PROPIO} quiere decir «lo tuyo», y en el panel nada es de quien mira: son
     * candidatos, y `/panel/**` exige un token de equipo. Así que no alcanza a nadie, que es
     * lo mismo que ya hacen los endpoints de la simulación.
     */
    public boolean noAlcanzaANadieEnElPanel() {
        return tipo == Tipo.PROPIO;
    }
}
