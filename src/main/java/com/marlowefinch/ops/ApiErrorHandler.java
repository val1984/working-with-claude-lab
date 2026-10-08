package com.marlowefinch.ops;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps invalid query parameters to {@code 400} with a body of {@code {"errors": [...]}}. */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(InvalidParametersException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, List<String>> invalidParameters(InvalidParametersException e) {
        return Map.of("errors", e.errors());
    }
}
