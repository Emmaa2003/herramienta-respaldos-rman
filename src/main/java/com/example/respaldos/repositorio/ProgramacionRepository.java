package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Programacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProgramacionRepository extends JpaRepository<Programacion, Long> {

    Optional<Programacion> findByEstrategiaId(Long estrategiaId);

    /** Programaciones que el programador debe revisar: activas y de estrategias activas. */
    @Query("select p.id from Programacion p where p.activa = true and p.estrategia.activa = true order by p.id")
    List<Long> idsActivas();
}
