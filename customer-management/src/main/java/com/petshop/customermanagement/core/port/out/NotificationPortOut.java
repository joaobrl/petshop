package com.petshop.customermanagement.core.port.out;

public interface NotificationPortOut {
    void sendPasswordReset(String to, String name, String newPassword);

    void sendRegistrationConfirmation(String to, String name);

    /** SDD-020: e-mail com o link de confirmação (token em claro no link). */
    void sendEmailVerification(String to, String name, String verificationLink);
}
