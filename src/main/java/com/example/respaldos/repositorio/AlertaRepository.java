package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.EstadoAlerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    boolean existsByEstrategiaId(Long estrategiaId);

    boolean existsByEstrategiaIdAndCodigoAndEstado(Long estrategiaId, String codigo, EstadoAlerta estado);

    List<Alerta> findByEstrategiaIdOrderByIdDesc(Long estrategiaId);

    List<Alerta> findByEstado(EstadoAlerta estado);
}
