package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.core.domain.Customer;

public interface EmailVerificationPortIn {

    /** Gera um token novo para o cliente e publica o e-mail com o link de verificação. */
    void issueVerification(Customer customer);

    /**
     * Confirma o e-mail dono do token. Token inexistente, expirado ou já usado
     * lança InvalidVerificationTokenException (400).
     */
    void verifyEmail(String token);

    /**
     * Sempre "silenciosa": não revela se o e-mail existe nem se já está
     * verificado. Só reemite quando há cliente com esse e-mail ainda não
     * verificado; nesse caso os tokens anteriores deixam de valer.
     */
    void resendVerification(String email);
}
