package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {
}
