package com.bis.assistant.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({UnauthorizedException.class, AccessDeniedException.class})
    ProblemDetail unauthorized(RuntimeException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail badRequest(Exception ex) {
        String msg;
        if (ex instanceof MethodArgumentNotValidException mve) {
            msg = mve.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        } else if (ex instanceof HttpMessageNotReadableException) {
            msg = "Malformed request payload or invalid JSON format.";
        } else {
            msg = ex.getMessage();
        }
        return problem(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(RagServiceException.class)
    ProblemDetail ragError(RagServiceException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
            "AI service temporarily unavailable. Please try again shortly.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail generic(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
