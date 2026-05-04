package com.proxy.exchangerate.exception;

/**
 * Exception métier — Erreur lors de la récupération ou du traitement des taux de change.
 */
public class ExchangeRateException extends RuntimeException {

    public ExchangeRateException(String message) {
        super(message);
    }

    public ExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
