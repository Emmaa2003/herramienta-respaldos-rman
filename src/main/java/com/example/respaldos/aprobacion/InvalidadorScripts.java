package com.example.respaldos.aprobacion;

import com.example.respaldos.generador.HuellaConfiguracion;
import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cuando cambia algo que afecta al script (base, QUE, COMO o destino), los scripts
 * pendientes o aprobados de esa estrategia dejan de representarla y se invalidan.
 * Asi una aprobacion nunca se aplica a una configuracion distinta de la que se reviso.
 */
@Component
public class InvalidadorScripts {

    private final ScriptRmanRepository scripts;

    public InvalidadorScripts(ScriptRmanRepository scripts) {
        this.scripts = scripts;
    }

    /** @return cuantos scripts se invalidaron */
    public int invalidarSiCambio(Estrategia estrategia) {
        if (estrategia.getId() == null) {
            return 0;
        }
        String huella = HuellaConfiguracion.de(estrategia);
        int invalidados = 0;
        for (ScriptRman s : scripts.findByEstrategiaIdAndEstadoIn(estrategia.getId(),
                List.of(EstadoScript.GENERADO, EstadoScript.APROBADO))) {
            if (!s.getHashConfiguracion().equals(huella)) {
                s.setEstado(EstadoScript.INVALIDADO);
                invalidados++;
            }
        }
        return invalidados;
    }
}
