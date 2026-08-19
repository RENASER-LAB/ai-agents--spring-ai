package com.renaser.ai.ai_engine.seguridad.dto;

// El «solo lo suyo» convertido en algo que una query puede usar. @PreAuthorize decide
// si se puede llamar; esto decide QUÉ FILAS se ven. Un permiso con alcance no es un
// sí o un no: es un WHERE.
public record FiltroAlcance(Tipo tipo, Long usuarioId) {

    public enum Tipo { TODO, SUS_VACANTES, PROPIO }

    public static FiltroAlcance desde(String alcance, Long usuarioId) {
        return new FiltroAlcance(Tipo.valueOf(alcance), usuarioId);
    }

    // Para las queries de bandeja: null significa «no filtres por responsable»
    public Long responsableOFiltroNulo() {
        return tipo == Tipo.SUS_VACANTES ? usuarioId : null;
    }
}
