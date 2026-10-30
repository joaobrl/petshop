package com.petshop.order_service.api.rest.handler;

import com.petshop.commons.handler.BaseExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class OrderExceptionHandler extends BaseExceptionHandler {

    // O checkout trava o carrinho (SELECT ... FOR UPDATE com timeout). Quem
    // esperou demais pelo lock de outro checkout do mesmo cliente recebe 409
    // em vez de cair no 500 genérico — tentar de novo é seguro.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleLockTimeout(PessimisticLockingFailureException ex) {
        log.warn("Checkout lock not acquired: {}", ex.getMostSpecificCause().getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Já existe uma finalização de compra em andamento para este carrinho. Tente novamente.");
        problem.setTitle("Resource conflict");
        return problem;
    }
}
