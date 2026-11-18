package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * El brochure de la empresa en PDF (V86): el perfil público pasado a papel, armado con lo
 * guardado en «Perfil de la empresa» en la plantilla y el color elegidos.
 *
 * <p><b>Siempre al día y nunca guardado.</b> No se publica ni se guarda en el almacén: se arma al
 * pedirlo. Para que pedirlo mil veces no cargue el servidor, se recuerda en memoria el último PDF
 * de cada empresa y solo se vuelve a armar cuando cambia algo de lo que lleva (lo guardado en el
 * módulo, sus sedes, su logo o la fecha del pie).
 *
 * <p><b>Lo mínimo es «Quiénes somos».</b> El nombre siempre está y sin logo salen las iniciales.
 */
public interface ServicioBrochure {

    /** El texto del 409 del panel cuando falta lo mínimo. */
    String FALTA_QUIENES_SOMOS = "Falta «Quiénes somos»: guárdalo y vuelve a descargar el brochure.";

    /**
     * El brochure de la empresa de quien ha entrado, con lo guardado.
     *
     * @throws IllegalStateException (409) si la empresa no tiene «Quiénes somos» guardado
     */
    ArchivoDelBrochure delPanel(ContextoUsuario quien);

    /**
     * El brochure de una empresa para el perfil público, o vacío si le falta lo mínimo. Quien
     * llama ya comprobó que la empresa está activa.
     */
    Optional<ArchivoDelBrochure> dePublica(Organizacion empresa);

    /** Si la empresa tiene lo mínimo para su brochure: «Quiénes somos» guardado. */
    static boolean tieneLoMinimo(Organizacion empresa) {
        return empresa != null && ContenidoDelBrochure.hay(empresa.getQuienesSomos());
    }

    /**
     * El PDF y su nombre, «Brochure - {empresa}.pdf». El {@code toString} no imprime los bytes.
     */
    record ArchivoDelBrochure(String nombre, byte[] contenido) {

        @Override
        public boolean equals(Object otro) {
            return otro instanceof ArchivoDelBrochure a && Objects.equals(nombre, a.nombre)
                    && Arrays.equals(contenido, a.contenido);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nombre, Arrays.hashCode(contenido));
        }

        @Override
        public String toString() {
            return "ArchivoDelBrochure[nombre=" + nombre + ", bytes="
                    + (contenido == null ? 0 : contenido.length) + "]";
        }
    }
}
