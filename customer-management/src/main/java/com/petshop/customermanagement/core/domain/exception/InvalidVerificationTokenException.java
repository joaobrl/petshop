package com.petshop.customermanagement.core.domain.exception;

/**
 * Token de verificação de e-mail inexistente, expirado ou já usado (SDD-020).
 * Estende IllegalArgumentException para cair no handler de 400 do
 * BaseExceptionHandler, que devolve a mensagem como {@code detail}.
 */
public class InvalidVerificationTokenException extends IllegalArgumentException {

    public InvalidVerificationTokenException(String message) {
        super(message);
    }
}
