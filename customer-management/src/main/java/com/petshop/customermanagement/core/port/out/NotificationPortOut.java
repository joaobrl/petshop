package com.petshop.customermanagement.core.port.out;

public interface NotificationPortOut {
    void sendPasswordReset(String to, String name, String newPassword);

    void sendRegistrationConfirmation(String to, String name);
}
