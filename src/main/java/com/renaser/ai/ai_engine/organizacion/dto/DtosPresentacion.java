package com.renaser.ai.ai_engine.organizacion.dto;

import java.util.List;
import java.util.Map;

/** Los contratos del módulo «Perfil de la empresa» del panel (V85). */
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
     */
    public record GuardarPresentacion(String sector, String tamano, Integer anioFundacion,
                                      String quienesSomos, String propositoEmpresa,
                                      List<String> beneficios, String videoPresentacion,
                                      String paginaWeb, Map<String, String> redes) {}

    /**
     * Lo que el módulo enseña: lo guardado, con los dos datos de solo lectura —el nombre y las
     * sedes— y lo que la vista previa necesita para pintarse como el perfil público.
     *
     * @param codigo  el de la empresa, para «Ver perfil público ↗» ({@code /empresas/<código>})
     * @param redes   el enlace de cada red que tiene, por su código
     * @param logo    la dirección pública de la imagen, o nulo si no tiene
     * @param ciudad  la provincia de la primera sede activa, como la dice el perfil público
     */
    public record PresentacionPanel(String codigo, String nombre, String sector, String tamano,
                                    String tamanoTexto, Integer anioFundacion,
                                    String quienesSomos, String propositoEmpresa,
                                    List<String> beneficios, String videoPresentacion,
                                    String paginaWeb, Map<String, String> redes, String logo,
                                    String portada, List<SedeDeLaPresentacion> sedes,
                                    String ciudad) {}

    /** Una sede activa, de solo lectura: se gestionan en Configuración → Sedes. */
    public record SedeDeLaPresentacion(String nombre, String provincia) {}

    /**
     * Si la empresa tiene video de presentación: el formulario de la vacante lo dice con el
     * chip «Se usará el video de la empresa» cuando el «Video del puesto» está vacío.
     */
    public record VideoDeLaEmpresa(boolean tieneVideo) {}
}
