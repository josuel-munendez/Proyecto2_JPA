package com.example.servicio.exception;

/**
 * Conflicto de versión (bloqueo optimista).
 *
 * Se lanza cuando el cliente envía una `version` que ya no es la vigente: otra
 * sesión de Spring, o un save() de Django sobre la misma tabla compartida,
 * actualizó el producto mientras este formulario estaba abierto.
 *
 * Mapeo HTTP: 409 CONFLICT (GlobalExceptionHandler). No es 400 porque la
 * petición era válida; el problema es el estado del recurso en el momento de
 * aplicarla, y 409 es lo que le dice al frontend "recargá y reintentá" en vez
 * de "arreglá los datos".
 */
public class VersionConflictException extends RuntimeException {

    private final Long versionEsperada;
    private final Long versionActual;

    public VersionConflictException(Long versionEsperada, Long versionActual) {
        super("El producto fue modificado por otro usuario (version esperada "
                + versionEsperada + ", actual " + versionActual
                + "). Recargue para ver los cambios mas recientes.");
        this.versionEsperada = versionEsperada;
        this.versionActual = versionActual;
    }

    public Long getVersionEsperada() {
        return versionEsperada;
    }

    public Long getVersionActual() {
        return versionActual;
    }
}
