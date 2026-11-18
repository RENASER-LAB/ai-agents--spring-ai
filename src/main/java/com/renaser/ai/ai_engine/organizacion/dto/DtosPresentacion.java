package com.renaser.ai.ai_engine.organizacion.dto;

import java.util.List;
import java.util.Map;

/** Los contratos del módulo «Perfil de la empresa» del panel (V85, y V86 para el brochure). */
public final class DtosPresentacion {

    private DtosPresentacion() {
    }

    /**
     * Lo que manda «Guardar»: todo junto, y todo opcional.
     *
     * <p>Reemplaza lo que había: un campo ausente o vacío se guarda como «sin dato», igual que
     * los beneficios que no vienen y las redes que no están en el mapa. El logo y la portada no
     * van aquí: se suben aparte, por {@code /presentacion/imagenes/{logo|portada}}.
     *
     * @param tamano    uno de {@code TamanoDeEmpresa}, o vacío
     * @param redes     el enlace de cada red, por su código ({@code FACEBOOK}, {@code INSTAGRAM},
     *                  {@code LINKEDIN}, {@code TIKTOK}, {@code YOUTUBE})
     * @param mision    hasta 400 caracteres, o vacía (V86)
     * @param vision    hasta 400 caracteres, o vacía (V86)
     * @param plantillaBrochure uno de {@code PlantillaDelBrochure}. Ausente, se queda la que había:
     *                  no existe «sin plantilla»
     * @param colorBrochure     uno de {@code ColorDelBrochure}. Ausente, se queda el que había
     */
    public record GuardarPresentacion(String sector, String tamano, Integer anioFundacion,
                                      String quienesSomos, String propositoEmpresa,
                                      List<String> beneficios, String videoPresentacion,
                                      String paginaWeb, Map<String, String> redes,
                                      String mision, String vision, String plantillaBrochure,
                                      String colorBrochure) {}

    /**
     * Lo que guarda la pestaña «Brochure» al elegir: solo la plantilla y el color, sin «Guardar».
     * Los dos son obligatorios y del catálogo; si no, 400 y no se guarda nada.
     *
     * @param plantillaBrochure uno de {@code PlantillaDelBrochure}
     * @param colorBrochure     uno de {@code ColorDelBrochure}
     */
    public record GuardarBrochure(String plantillaBrochure, String colorBrochure) {}

    /**
     * Lo que el módulo enseña: lo guardado, con los dos datos de solo lectura —el nombre y las
     * sedes— y lo que la vista previa necesita para pintarse como el perfil público.
     *
     * @param codigo  el de la empresa, para «Ver perfil público ↗» ({@code /empresas/<código>})
     * @param redes   el enlace de cada red que tiene, por su código
     * @param logo    la dirección pública de la imagen, o nulo si no tiene
     * @param ciudad  la provincia de la primera sede activa, como la dice el perfil público
     * @param plantillaBrochure la plantilla elegida para el brochure en PDF (V86)
     * @param colorBrochure     el color elegido para el brochure en PDF (V86)
     */
    public record PresentacionPanel(String codigo, String nombre, String sector, String tamano,
                                    String tamanoTexto, Integer anioFundacion,
                                    String quienesSomos, String propositoEmpresa,
                                    List<String> beneficios, String videoPresentacion,
                                    String paginaWeb, Map<String, String> redes, String logo,
                                    String portada, List<SedeDeLaPresentacion> sedes,
                                    String ciudad, String mision, String vision,
                                    String plantillaBrochure, String colorBrochure) {}

    /** Una sede activa, de solo lectura: se gestionan en Configuración → Sedes. */
    public record SedeDeLaPresentacion(String nombre, String provincia) {}

    /**
     * Si la empresa tiene video de presentación: el formulario de la vacante lo dice con el
     * chip «Se usará el video de la empresa» cuando el «Video del puesto» está vacío.
     */
    public record VideoDeLaEmpresa(boolean tieneVideo) {}
}
