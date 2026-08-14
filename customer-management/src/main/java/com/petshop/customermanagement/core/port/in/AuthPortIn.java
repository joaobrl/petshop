package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.api.rest.dto.LoginResponseDto;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;

public interface AuthPortIn {

    LoginResponseDto login(LoginRequestDto request);

    /**
     * Sempre "silenciosa": não revela se o email existe. Se email+cpf não
     * baterem com nenhum cliente, simplesmente não faz nada — quem chama
     * (AuthControllerImpl) devolve a mesma resposta 200 genérica de
     * qualquer forma.
     */
    void forgotPassword(ForgotPasswordRequestDto request);

    void changePassword(ChangePasswordRequestDto request);
}
