package org.example.parser;

/**
 * Отдельный тип исключения вместо голого Exception в сигнатурах.
 * Позволяет вызывающему коду ловить именно ошибки парсера Kwork,
 * не пряча под одной и той же catch-веткой сетевые и логические баги.
 */
public class KworkParsingException extends RuntimeException {

    public KworkParsingException(String message) {
        super(message);
    }

    public KworkParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
