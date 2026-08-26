package com.example.financial.aggregation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgument_returns400WithMessage() {
        var response = handler.handleBadRequest(new IllegalArgumentException("'from' must not be after 'to'"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("'from' must not be after 'to'", response.getBody().message());
    }

    @Test
    void accessDenied_returns403WithMessage() {
        var response = handler.handleForbidden(new AccessDeniedException("Caller owns none of the requested accounts"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Caller owns none of the requested accounts", response.getBody().message());
    }

    @Test
    void missingParam_returns400() {
        var response = handler.handleMissingParam(
            new MissingServletRequestParameterException("accountIds", "List"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().message());
    }

    @Test
    void generalException_returns500WithGenericMessage() {
        var response = handler.handleGeneral(new RuntimeException("unexpected npe"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected error occurred", response.getBody().message());
    }
}
