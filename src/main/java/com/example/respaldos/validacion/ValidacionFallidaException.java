package com.example.respaldos.validacion;

/** La estrategia tiene hallazgos bloqueantes: no se puede generar ni ejecutar su script (HTTP 422). */
public class ValidacionFallidaException extends RuntimeException {

    private final ResultadoValidacion resultado;

    public ValidacionFallidaException(ResultadoValidacion resultado) {
        super("La estrategia no paso la validacion: " + resultado.bloqueantes().size()
                + " problema(s) bloqueante(s). Corrijalos antes de generar el script.");
        this.resultado = resultado;
    }

    public ResultadoValidacion getResultado() {
        return resultado;
    }
}
