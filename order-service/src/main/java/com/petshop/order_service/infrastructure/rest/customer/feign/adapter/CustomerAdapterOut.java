package com.petshop.order_service.infrastructure.rest.customer.feign.adapter;

import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.infrastructure.rest.customer.feign.CustomerFeign;
import com.petshop.order_service.infrastructure.security.TokenPropagation;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerAdapterOut implements CustomerPortOut {

    private final CustomerFeign customerFeign;
    private final TokenPropagation tokenPropagation;

    @Override
    public Optional<CustomerInfo> findCustomerById(UUID customerId, String token) {
        try {
            var response = customerFeign.getCustomerById(customerId, tokenPropagation.resolveAuthorizationHeader(token));
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new CustomerInfo(response.id(), response.name(), response.email()));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            log.error("Falha ao consultar MS customer-management para o cliente {}: {}", customerId, e.getMessage());
            return Optional.empty();
        }
    }
}
