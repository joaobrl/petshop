package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper.CustomerMapper;
import com.petshop.booking_service.infrastructure.security.TokenPropagation;
import com.petshop.commons.exception.DependencyUnavailableException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerManagementAdapterOut implements CustomerManagementPortOut {

    private static final String STAFF_SERVICE = "staff-service";
    private static final String STAFF_UNAVAILABLE_MESSAGE =
            "Não foi possível consultar a agenda de funcionários no momento. Tente novamente.";

    private final CustomerManagementFeign feignClient;
    private final CustomerMapper mapper;
    private final TokenPropagation tokenPropagation;

    @Override
    public List<StaffAssignment> getScheduledStaff(LocalDateTime dateTime, String role, String token) {
        String formattedDate = mapper.toQueryParam(dateTime);

        try {
            var response = feignClient.getSchedule(formattedDate, role, tokenPropagation.resolveAuthorizationHeader(token));

            if (response == null) {
                log.warn("MS staff-service devolveu resposta vazia pra data: {}", formattedDate);
                return List.of();
            }

            return response.stream()
                    .map(staff -> new StaffAssignment(staff.id(), staff.name()))
                    .toList();

        } catch (FeignException e) {
            int status = e.status();

            // 401/403: o staff-service recusou o token de serviço — bug de configuração nosso, não culpa do cliente.
            if (status == 401 || status == 403) {
                log.error("MS staff-service recusou a chamada (token de serviço/configuração). Status: {}. Erro: {}",
                        status, e.getMessage());
                throw new DependencyUnavailableException(STAFF_SERVICE, STAFF_UNAVAILABLE_MESSAGE, e);
            }

            // -1 = sem resposta (conexão recusada/timeout, inclusive RetryableException); 5xx = falha do outro lado;
            // 408/429 = falha transitória. Nenhum desses significa "ninguém escalado".
            if (status == -1 || status >= 500 || status == 408 || status == 429) {
                log.error("Falha ao consultar MS staff-service. Status: {}. Erro: {}", status, e.getMessage());
                throw new DependencyUnavailableException(STAFF_SERVICE, STAFF_UNAVAILABLE_MESSAGE, e);
            }

            // Demais 4xx (400/404/...): contrato quebrado entre os serviços (bug nosso), não indisponibilidade.
            // Propaga a FeignException original (vira 500 genérico), em vez de mascarar como "agenda cheia".
            log.error("MS staff-service rejeitou a consulta de escala. Status: {}. Erro: {}", status, e.getMessage());
            throw e;
        }
    }
}
