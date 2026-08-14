package com.petshop.commons.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String resource, Object identifier) {
        super(resource + " not found with identifier: " + identifier);
    }
}