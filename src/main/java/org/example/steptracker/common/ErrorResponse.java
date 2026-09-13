package org.example.steptracker.common;

import java.util.Map;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, Map<String, Object> details) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message, Map.of()));
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new ErrorBody(code, message, details));
    }
}
