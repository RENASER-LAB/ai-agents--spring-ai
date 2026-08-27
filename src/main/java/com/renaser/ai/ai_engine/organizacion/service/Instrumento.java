package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;

/**
 * Los cuatro instrumentos de evaluación que una empresa puede personalizar.
 *
 * <p>Cada uno sabe leer y escribir su bandera en {@link Organizacion}: así ni el
 * resolutor ni la personalización tienen un switch que se olvide de actualizar cuando
 * aparezca un instrumento nuevo.
 */
public enum Instrumento {

    BANCO {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isBancoPropio();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setBancoPropio(propio);
        }
    },
    PESOS {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isPesosPropios();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setPesosPropios(propio);
        }
    },
    PLANTILLA_EVALUACION {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isPlantillasEvaluacionPropias();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setPlantillasEvaluacionPropias(propio);
        }
    },
    PRUEBA {
        @Override public boolean esPropio(Organizacion organizacion) {
            return organizacion.isPruebasPuestoPropias();
        }
        @Override public void poner(Organizacion organizacion, boolean propio) {
            organizacion.setPruebasPuestoPropias(propio);
        }
    };

    public abstract boolean esPropio(Organizacion organizacion);

    public abstract void poner(Organizacion organizacion, boolean propio);
}
