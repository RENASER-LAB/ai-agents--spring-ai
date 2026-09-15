package com.renaser.ai.ai_engine.consentimiento.entity;

/**
 * Los tres permisos que puede firmar un candidato, y con quién firma cada uno.
 *
 * <p>Son tres y no uno porque los responsables son distintos, y la ley 29733 exige que
 * cada quien que trata datos esté nombrado y consentido. Juntarlos en una sola casilla
 * sería pedir un permiso que nadie dio.
 *
 * <p>El nombre de la constante es el que viaja a la base: {@code texto_consentimiento.tipo}
 * lo guarda tal cual y su CHECK enumera estos tres (V54). Antes de la V54 estos valores
 * andaban sueltos como literales por cuatro servicios distintos, y el de la cuenta usaba
 * {@link #PROCESO}, que habla de «esta vacante» cuando todavía no hay ninguna.
 */
public enum TipoConsentimiento {

    /**
     * Lo que se acepta con Renaser al crear la cuenta: la cuenta, el perfil, la
     * inteligencia artificial, los proveedores de fuera del país, el plazo y los derechos.
     * Solo lo tiene la organización plataforma — las demás no tienen cuentas, tienen
     * vacantes.
     */
    PLATAFORMA,

    /**
     * Lo que se acepta con LA EMPRESA de la vacante al postular: que ella decide sobre esta
     * postulación. Uno por empresa y uno por postulación; postular a tres empresas son tres
     * firmas, cada una a nombre de la suya.
     */
    PROCESO,

    /**
     * El permiso opcional para conservar el perfil y avisar de otras vacantes. Es de la
     * plataforma, se retira por separado y retirarlo no toca ningún proceso en curso.
     */
    FUTUROS_CONTACTOS;

    /** El valor tal como viaja a la base y como lo escribe quien publica desde el panel. */
    public String codigo() {
        return name();
    }

    /** Si ese texto de fuera —el del panel, el de un JSON— nombra un tipo que existe. */
    public static boolean existe(String codigo) {
        for (TipoConsentimiento tipo : values()) {
            if (tipo.name().equals(codigo)) {
                return true;
            }
        }
        return false;
    }
}
