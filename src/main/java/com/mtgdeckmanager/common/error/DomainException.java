package com.mtgdeckmanager.common.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        if (status != HttpStatus.NOT_FOUND && status != HttpStatus.CONFLICT
                && status != HttpStatus.UNPROCESSABLE_ENTITY) {
            throw new IllegalArgumentException("Unsupported domain error status");
        }
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
