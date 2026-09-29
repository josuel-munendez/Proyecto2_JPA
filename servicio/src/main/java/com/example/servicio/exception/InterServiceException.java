package com.example.servicio.exception;

/**
 * Fallo al hablar con Django durante una operacion que no puede completarse
 * sin el (por ejemplo, la limpieza de su parte de la cascada de purga).
 *
 * Se distingue de un error de negocio a proposito: el producto no tiene nada
 * de malo, la dependencia este caida o respondiendo mal. Por eso se mapea a
 * 503 SERVICE_UNAVAILABLE y no a 400, para que el frontend pueda reintentar
 * mas tarde en vez de pedirle al usuario que corrija unos datos.
 *
 * El fail-safe esta en InterServiceClient: si Django no confirma la limpieza,
 * la purga se aborta y el producto queda intacto. Es preferible no purgar a
 * purgar dejando referencias huerfanas en el PostgreSQL de Django.
 */
public class InterServiceException extends RuntimeException {

    public InterServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
