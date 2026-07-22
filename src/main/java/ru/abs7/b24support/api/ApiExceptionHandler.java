package ru.abs7.b24support.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.abs7.b24support.bitrix.BitrixRestException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BitrixRestException.class)
    public ProblemDetail handleBitrixRestException(BitrixRestException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setTitle("Ошибка Bitrix24 REST API");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
