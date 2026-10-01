package com.example.respaldos.generador;

import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.infraestructura.ResultadoProceso;
import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidacionFallidaException;
import com.example.respaldos.validacion.ValidadorEstrategia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flujo de generacion: validacion, construccion del script, verificacion de sintaxis
 * con RMAN y se guarda como nueva version en estado GENERADO.
 * Ningun paso ejecuta el script: eso solo ocurre despues de la aprobacion.
 */
@Service
@Transactional
public class ScriptService {

    private final EstrategiaRepository estrategias;
    private final ScriptRmanRepository scripts;
    private final ValidadorEstrategia validador;
    private final GeneradorScriptRman generador;
    private final ClienteContenedor cliente;

    public ScriptService(EstrategiaRepository estrategias, ScriptRmanRepository scripts,
                         ValidadorEstrategia validador, GeneradorScriptRman generador, ClienteContenedor cliente) {
        this.estrategias = estrategias;
        this.scripts = scripts;
        this.validador = validador;
        this.generador = generador;
        this.cliente = cliente;
    }

    public GeneracionRespuesta generar(Long estrategiaId) {
        Estrategia estrategia = buscarEstrategia(estrategiaId);

        // Regla 2: validar antes de generar.
        ResultadoValidacion validacion = validador.validar(estrategia);
        if (!validacion.valida()) {
            throw new ValidacionFallidaException(validacion);
        }

        int version = scripts.ultimaVersion(estrategiaId) + 1;
        ScriptGenerado generado = generador.generar(estrategia, version, LocalDateTime.now());

        // RMAN revisa la sintaxis sin conectarse a la base. Si falla, es un error del generador.
        ResultadoProceso sintaxis = cliente.verificarSintaxisRman(
                estrategia.getBaseDatos().getContenedor(), generado.contenido());
        if (!sintaxis.terminoSinError()) {
            throw new IllegalStateException("El script generado no paso la verificacion de sintaxis de RMAN: "
                    + sintaxis.salida().strip());
        }

        // Una version nueva reemplaza a las que todavia nadie aprobo.
        for (ScriptRman anterior : scripts.findByEstrategiaIdAndEstado(estrategiaId, EstadoScript.GENERADO)) {
            anterior.setEstado(EstadoScript.INVALIDADO);
        }

        ScriptRman script = new ScriptRman();
        script.setEstrategia(estrategia);
        script.setVersion(version);
        script.setContenido(generado.contenido());
        script.setHashContenido(HuellaConfiguracion.sha256(generado.contenido()));
        script.setHashConfiguracion(HuellaConfiguracion.de(estrategia));
        script.setEstado(EstadoScript.GENERADO);
        scripts.saveAndFlush(script);

        return new GeneracionRespuesta(ScriptRespuesta.de(script), generado.pasos(), validacion.hallazgos(),
                "RMAN checksyntax: el script no tiene errores de sintaxis.");
    }

    @Transactional(readOnly = true)
    public List<ScriptRespuesta> listar(Long estrategiaId) {
        buscarEstrategia(estrategiaId);
        return scripts.findByEstrategiaIdOrderByVersionDesc(estrategiaId).stream().map(ScriptRespuesta::de).toList();
    }

    @Transactional(readOnly = true)
    public ScriptRespuesta obtener(Long scriptId) {
        return scripts.findById(scriptId).map(ScriptRespuesta::de)
                .orElseThrow(() -> new NoEncontradoException("No existe el script con id " + scriptId + "."));
    }

    private Estrategia buscarEstrategia(Long id) {
        return estrategias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la estrategia con id " + id + "."));
    }
}
