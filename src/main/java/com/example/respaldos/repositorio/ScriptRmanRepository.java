package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.ScriptRman;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ScriptRmanRepository extends JpaRepository<ScriptRman, Long> {

    boolean existsByEstrategiaId(Long estrategiaId);

    List<ScriptRman> findByEstrategiaIdOrderByVersionDesc(Long estrategiaId);

    List<ScriptRman> findByEstrategiaIdAndEstado(Long estrategiaId, EstadoScript estado);

    List<ScriptRman> findByEstrategiaIdAndEstadoIn(Long estrategiaId, Collection<EstadoScript> estados);

    @Query("select coalesce(max(s.version), 0) from ScriptRman s where s.estrategia.id = :estrategiaId")
    int ultimaVersion(Long estrategiaId);
}
