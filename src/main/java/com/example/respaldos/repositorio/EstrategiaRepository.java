package com.example.respaldos.repositorio;

import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.TipoElemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EstrategiaRepository extends JpaRepository<Estrategia, Long> {

    boolean existsByBaseDatosId(Long baseDatosId);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /** true si alguna estrategia activa de la base incluye el elemento indicado. */
    @Query("""
            select count(e) > 0 from Estrategia e join e.elementos el
            where e.baseDatos.id = :baseDatosId and e.activa = true and el.tipoElemento = :tipo
            """)
    boolean existeActivaConElemento(Long baseDatosId, TipoElemento tipo);

    /** Igual que existeActivaConElemento, pero sin contar la estrategia indicada. */
    @Query("""
            select count(e) > 0 from Estrategia e join e.elementos el
            where e.baseDatos.id = :baseDatosId and e.activa = true and el.tipoElemento = :tipo
              and e.id <> :excluida
            """)
    boolean existeOtraActivaConElemento(Long baseDatosId, TipoElemento tipo, Long excluida);
}
