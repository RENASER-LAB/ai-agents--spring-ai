package com.renaser.ai.ai_engine.whatsapp.service;

/**
 * La consulta v2 no tiene nada que dar con esa referencia o ese cursor (V76): {@code 404
 * {codigo: "SIN_ACCESO", texto}}.
 *
 * <p>El mismo error para lo que no existe, lo que es de otro número u otra organización y lo que ya
 * no se puede consultar: distinguirlos le diría a quien prueba referencias cuáles son de alguien
 * (C-1).
 */
public class SinAccesoWhatsappException extends RuntimeException {

    public static final String CODIGO = "SIN_ACCESO";

    public static final String TEXTO = "No encontramos ese proceso entre los tuyos. Puedes ver "
            + "todos tus procesos en el portal, en «Mis procesos».";

    public SinAccesoWhatsappException() {
        super(TEXTO);
    }
}
