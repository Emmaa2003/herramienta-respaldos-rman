package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.BaseDatos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseDatosRepository extends JpaRepository<BaseDatos, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
