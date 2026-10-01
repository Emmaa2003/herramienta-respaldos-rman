package com.example.respaldos.infraestructura;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas sin Docker: un ejecutor falso registra los comandos y devuelve salidas preparadas. */
class ClienteContenedorTest {

    private final EjecutorFalso ejecutor = new EjecutorFalso();
    private final ClienteContenedor cliente = new ClienteContenedor(ejecutor, new PropiedadesDocker(
            "docker", List.of("oracle-xe"), Duration.ofHours(2), Duration.ofSeconds(30)));

    @Test
    void rmanSeEjecutaDentroDelContenedorConElScriptPorStdin() {
        ejecutor.responder(0, "Recovery Manager complete.");
        String script = "RUN {\n  BACKUP DATABASE;\n}\n";

        ResultadoProceso r = cliente.ejecutarRman("oracle-xe", script);

        assertThat(r.terminoSinError()).isTrue();
        assertThat(ejecutor.comando).containsExactly("docker", "exec", "-i", "oracle-xe", "rman", "target", "/");
        assertThat(ejecutor.entrada).isEqualTo(script);
        assertThat(ejecutor.tiempoMaximo).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void rechazaContenedoresNoPermitidosSinEjecutarNada() {
        assertThatThrownBy(() -> cliente.ejecutarRman("oracle-local", "BACKUP DATABASE;"))
                .isInstanceOf(AmbienteException.class)
                .hasMessageContaining("no esta permitido");
        assertThatThrownBy(() -> cliente.contenedorEnEjecucion(null))
                .isInstanceOf(AmbienteException.class);
        assertThat(ejecutor.comando).isNull();
    }

    @Test
    void rechazaRutasRelativasOConDosPuntos() {
        assertThatThrownBy(() -> cliente.tamanoArchivo("oracle-xe", "respaldos/x.bkp"))
                .isInstanceOf(AmbienteException.class);
        assertThatThrownBy(() -> cliente.espacioDisco("oracle-xe", "/opt/oracle/../../etc"))
                .isInstanceOf(AmbienteException.class);
        assertThatThrownBy(() -> cliente.tamanoArchivo("oracle-xe", "/tmp/a\nrm -rf /"))
                .isInstanceOf(AmbienteException.class);
        assertThat(ejecutor.comando).isNull();
    }

    @Test
    void tamanoArchivoDistingueExisteNoExisteYFalla() {
        ejecutor.responder(0, "1048576\n");
        assertThat(cliente.tamanoArchivo("oracle-xe", "/opt/oracle/oradata/respaldos/a.bkp")).contains(1048576L);
        assertThat(ejecutor.comando).containsExactly(
                "docker", "exec", "oracle-xe", "stat", "-c", "%s", "--", "/opt/oracle/oradata/respaldos/a.bkp");

        ejecutor.responder(1, "stat: cannot statx '/x.bkp': No such file or directory\n");
        assertThat(cliente.tamanoArchivo("oracle-xe", "/x.bkp")).isEmpty();

        ejecutor.responder(1, "Error response from daemon: container oracle-xe is not running\n");
        assertThatThrownBy(() -> cliente.tamanoArchivo("oracle-xe", "/x.bkp"))
                .isInstanceOf(AmbienteException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void espacioDiscoInterpretaLaSalidaDeDf() {
        ejecutor.responder(0, """
                Filesystem     1024-blocks     Used Available Capacity Mounted on
                /dev/sdd        1055762868 14273664 987785732       2% /opt/oracle/oradata
                """);

        EspacioDisco espacio = cliente.espacioDisco("oracle-xe", "/opt/oracle/oradata/respaldos");

        assertThat(espacio.puntoMontaje()).isEqualTo("/opt/oracle/oradata");
        assertThat(espacio.totalBytes()).isEqualTo(1055762868L * 1024);
        assertThat(espacio.disponiblesBytes()).isEqualTo(987785732L * 1024);
    }

    @Test
    void espacioDiscoFallaSiLaRutaNoExiste() {
        ejecutor.responder(1, "df: /noexiste: No such file or directory\n");

        assertThatThrownBy(() -> cliente.espacioDisco("oracle-xe", "/noexiste"))
                .isInstanceOf(AmbienteException.class)
                .hasMessageContaining("No such file");
    }

    @Test
    void contenedorEnEjecucionLeeElEstadoDeDockerInspect() {
        ejecutor.responder(0, "true\n");
        assertThat(cliente.contenedorEnEjecucion("oracle-xe")).isTrue();

        ejecutor.responder(0, "false\n");
        assertThat(cliente.contenedorEnEjecucion("oracle-xe")).isFalse();

        ejecutor.responder(1, "Error: No such object: oracle-xe\n");
        assertThat(cliente.contenedorEnEjecucion("oracle-xe")).isFalse();
    }

    private static class EjecutorFalso implements EjecutorProcesos {
        List<String> comando;
        String entrada;
        Duration tiempoMaximo;
        private int codigo;
        private String salida;

        void responder(int codigo, String salida) {
            this.codigo = codigo;
            this.salida = salida;
        }

        @Override
        public ResultadoProceso ejecutar(List<String> comando, String entrada, Duration tiempoMaximo) {
            this.comando = new ArrayList<>(comando);
            this.entrada = entrada;
            this.tiempoMaximo = tiempoMaximo;
            LocalDateTime ahora = LocalDateTime.now();
            return new ResultadoProceso(codigo, salida, ahora, ahora, false);
        }
    }
}
