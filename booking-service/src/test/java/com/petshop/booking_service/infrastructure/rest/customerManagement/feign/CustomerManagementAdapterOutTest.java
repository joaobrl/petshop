package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto.StaffDto;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper.CustomerMapper;
import com.petshop.booking_service.infrastructure.security.TokenPropagation;
import com.petshop.commons.security.jwt.JwtService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.petshop.commons.exception.DependencyUnavailableException;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerManagementAdapterOutTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private CustomerManagementFeign feignClient;

    private CustomerManagementAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerManagementAdapterOut(feignClient, new CustomerMapper(), new TokenPropagation(JWT_SERVICE));
    }

    @Test
    void mapsFeignResponseToStaffAssignments() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var id = UUID.randomUUID();
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenReturn(List.of(new StaffDto(id, "Ciclana")));

        var result = adapter.getScheduledStaff(dateTime, "GROOMER", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).name()).isEqualTo("Ciclana");
    }

    @Test
    void mintsFreshServiceTokenWhenNoCallerTokenIsProvided() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenReturn(List.of());

        adapter.getScheduledStaff(dateTime, "GROOMER", null);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(feignClient).getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), captor.capture());
        var parsed = JWT_SERVICE.parse(captor.getValue().substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    void relaysCallerTokenWhenProvided() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var callerToken = "Bearer " + JWT_SERVICE.generateServiceToken("quem-chamou");
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenReturn(List.of());

        adapter.getScheduledStaff(dateTime, "GROOMER", callerToken);

        org.mockito.Mockito.verify(feignClient).getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), eq(callerToken));
    }

    @Test
    void returnsEmptyListWhenFeignReturnsNull() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString())).thenReturn(null);

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER", null)).isEmpty();
    }

    @Test
    void returnsEmptyListWhenFeignReturnsEmptyList() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString())).thenReturn(List.of());

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER", null)).isEmpty();
    }

    // ---------- SDD-011: falha ao consultar staff não pode virar "sem funcionário disponível" ----------

    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2026, 8, 15, 9, 0);

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/api/v1/availability/schedule",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private void stubFeignThrows(RuntimeException ex) {
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString())).thenThrow(ex);
    }

    private List<ILoggingEvent> captureErrorLogs(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(CustomerManagementAdapterOut.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // exceção é verificada pelo teste; aqui só interessa o log
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
    }

    @Test
    void throwsDependencyUnavailableWhenStaffServiceReturns500() {
        var cause = new FeignException.InternalServerError("boom", request(), null, null);
        stubFeignThrows(cause);

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isInstanceOfSatisfying(DependencyUnavailableException.class, ex -> {
                    assertThat(ex.getDependency()).isEqualTo("staff-service");
                    assertThat(ex.getUserMessage())
                            .isEqualTo("Não foi possível consultar a agenda de funcionários no momento. Tente novamente.");
                    assertThat(ex.getCause()).isSameAs(cause);
                });
    }

    @Test
    void throwsDependencyUnavailableWhenStaffServiceReturns503() {
        stubFeignThrows(new FeignException.ServiceUnavailable("unavailable", request(), null, null));

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void throwsDependencyUnavailableOnConnectionFailureWithStatusMinusOne() {
        var cause = new RetryableException(-1, "Connection refused", Request.HttpMethod.GET,
                new IOException("refused"), (Long) null, request());
        stubFeignThrows(cause);

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isInstanceOfSatisfying(DependencyUnavailableException.class,
                        ex -> assertThat(ex.getCause()).isSameAs(cause));
    }

    @Test
    void throwsDependencyUnavailableAndLogsErrorWhenServiceTokenIsRejected() {
        var cause401 = new FeignException.Unauthorized("no token", request(), null, null);
        stubFeignThrows(cause401);

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isInstanceOfSatisfying(DependencyUnavailableException.class,
                        ex -> assertThat(ex.getCause()).isSameAs(cause401));

        var cause403 = new FeignException.Forbidden("denied", request(), null, null);
        stubFeignThrows(cause403);

        var logs = captureErrorLogs(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFormattedMessage()).contains("403").contains("denied");
    }

    @Test
    void throwsDependencyUnavailableOnTransientClientErrors() {
        stubFeignThrows(new FeignException.TooManyRequests("slow down", request(), null, null));

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void rethrowsOriginalFeignExceptionOnOtherClientErrors() {
        var notFound = new FeignException.NotFound("no route", request(), null, null);
        stubFeignThrows(notFound);

        assertThatThrownBy(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null))
                .isSameAs(notFound);
    }

    @Test
    void logKeepsOriginalStatusAndMessage() {
        stubFeignThrows(new FeignException.InternalServerError("falha interna do staff", request(), null, null));

        var logs = captureErrorLogs(() -> adapter.getScheduledStaff(DATE_TIME, "GROOMER", null));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFormattedMessage()).contains("500").contains("falha interna do staff");
    }
}
