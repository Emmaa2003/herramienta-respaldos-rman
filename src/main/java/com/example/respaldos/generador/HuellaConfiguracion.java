package com.example.respaldos.generador;

import com.example.respaldos.modelo.Estrategia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Huellas SHA-256 para saber si un script sigue correspondiendo a su estrategia.
 * <p>
 * La huella de configuracion solo incluye lo que cambia el script (base, QUE, COMO y
 * destino). Cambiar el nombre, la prioridad, la retencion o la programacion no la altera,
 * asi que no invalida un script aprobado.
 */
public final class HuellaConfiguracion {

    private HuellaConfiguracion() {
    }

    public static String de(Estrategia e) {
        String elementos = e.getElementos().stream()
                .map(el -> el.getTipoElemento() + ":" + (el.getNombreObjeto() == null ? "" : el.getNombreObjeto()))
                .sorted()
                .collect(Collectors.joining(","));
        String canonica = String.join("|",
                "estrategia=" + e.getId(),
                "base=" + e.getBaseDatos().getId(),
                "contenedor=" + e.getBaseDatos().getContenedor(),
                "servicio=" + e.getBaseDatos().getServicio(),
                "tipo=" + e.getTipoRespaldo(),
                "comprimido=" + e.isComprimido(),
                "destino=" + e.getRutaDestino(),
                "dispositivo=" + e.getDispositivo(),
                "elementos=" + elementos);
        return sha256(canonica);
    }

    public static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
