package com.example.respaldos.modelo;

/**
 * Prioridad de la informacion que protege la estrategia.
 * <p>
 * Criterios del grupo para asignarla:
 * <ul>
 *   <li>ALTA: su perdida detiene o afecta gravemente la operacion (p. ej. produccion,
 *       transacciones, datos financieros). Se espera al menos un respaldo cada 24 h,
 *       incluir los archived redo logs y verificar cada respaldo.</li>
 *   <li>MEDIA: importante, pero la operacion tolera perder horas o un dia de cambios
 *       (p. ej. sistemas internos, reportes). Se espera al menos un respaldo por semana.</li>
 *   <li>BAJA: se puede reconstruir o recuperar por otros medios (p. ej. pruebas,
 *       datos cargados desde otra fuente). Se espera al menos un respaldo por mes.</li>
 * </ul>
 * La prioridad no decide por si sola el tipo de respaldo; define cuanto tiempo puede
 * pasar sin un respaldo exitoso antes de alertar (estrategia sin respaldo reciente).
 */
public enum Prioridad {
    ALTA("Informacion critica para la continuidad de la operacion",
            "Su perdida detiene o afecta gravemente la operacion", 24),
    MEDIA("Informacion importante cuya recuperacion puede admitir mayores tiempos",
            "La operacion tolera perder horas o un dia de cambios", 24 * 7),
    BAJA("Informacion de menor impacto o que puede ser reconstruida",
            "Se puede reconstruir o recuperar por otros medios", 24 * 30);

    private final String descripcion;
    private final String criterio;
    private final int horasMaximasSinRespaldo;

    Prioridad(String descripcion, String criterio, int horasMaximasSinRespaldo) {
        this.descripcion = descripcion;
        this.criterio = criterio;
        this.horasMaximasSinRespaldo = horasMaximasSinRespaldo;
    }

    public String getDescripcion() { return descripcion; }

    public String getCriterio() { return criterio; }

    /** Horas sin un respaldo exitoso a partir de las cuales se considera que no hay respaldo reciente. */
    public int getHorasMaximasSinRespaldo() { return horasMaximasSinRespaldo; }
}
