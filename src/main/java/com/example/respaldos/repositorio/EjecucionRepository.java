package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Ejecucion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjecucionRepository extends JpaRepository<Ejecucion, Long> {
}
