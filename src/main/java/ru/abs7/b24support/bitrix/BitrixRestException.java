package ru.abs7.b24support.bitrix;

public class BitrixRestException extends RuntimeException {

    public BitrixRestException(String message) {
        super(message);
    }

    public BitrixRestException(String message, Throwable cause) {
        super(message, cause);
    }
}
