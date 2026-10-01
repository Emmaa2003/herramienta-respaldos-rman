package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Ejecucion;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.TipoRespaldo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EjecucionRepository extends JpaRepository<Ejecucion, Long> {

    boolean existsByBaseDatosIdAndTipoRespaldoAndEstadoIn(Long baseDatosId, TipoRespaldo tipoRespaldo,
                                                          Collection<EstadoEjecucion> estados);

    boolean existsByBaseDatosIdAndEstado(Long baseDatosId, EstadoEjecucion estado);

    List<Ejecucion> findByEstado(EstadoEjecucion estado);

    List<Ejecucion> findAllByOrderByFechaInicioDesc();
}
