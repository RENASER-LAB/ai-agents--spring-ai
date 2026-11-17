package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.PerfilDeEmpresa;

/**
 * El perfil público de una empresa (V85): {@code /empresas/<código>} en el portal, sin cuenta,
 * igual que el tablón. Solo de las empresas activas: una suspendida, o un código que no existe,
 * no tiene perfil para nadie.
 */
public interface ServicioEmpresasPortal {

    /** La presentación y sus vacantes publicadas. 404 si no existe o no está activa. */
    PerfilDeEmpresa perfil(String codigo);

    /**
     * Los bytes del logo o la portada, y su tipo. 404 si la empresa no existe o no está activa,
     * si el nombre no es {@code logo} ni {@code portada}, o si no tiene esa imagen. Nunca otro
     * archivo: se busca por el id que guarda la propia empresa y con su organización.
     */
    Imagen imagen(String codigo, String cual);

    /** El contenido de una imagen pública. El {@code toString} no imprime los bytes. */
    record Imagen(byte[] bytes, String tipo) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Imagen i && java.util.Arrays.equals(bytes, i.bytes)
                    && java.util.Objects.equals(tipo, i.tipo);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(java.util.Arrays.hashCode(bytes), tipo);
        }

        @Override
        public String toString() {
            return "Imagen[tipo=" + tipo + ", bytes=" + (bytes == null ? 0 : bytes.length) + "]";
        }
    }
}
