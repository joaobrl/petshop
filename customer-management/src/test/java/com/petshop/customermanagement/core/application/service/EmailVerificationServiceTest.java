package com.petshop.customermanagement.core.application.service;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.EmailVerificationToken;
import com.petshop.customermanagement.core.domain.exception.InvalidVerificationTokenException;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.EmailVerificationTokenPortOut;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.commons.exception.DependencyUnavailableException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String FRONTEND_BASE_URL = "https://petshop.test";
    private static final String LINK_PREFIX = FRONTEND_BASE_URL + "/verify-email?token=";
    private static final String RAW_TOKEN = "raw-token-value";

    @Mock
    private CustomerPortOut customerPortOut;

    @Mock
    private EmailVerificationTokenPortOut tokenPortOut;

    @Mock
    private NotificationPortOut notificationPortOut;

    @Mock
    private IdentityProviderPortOut identityProviderPortOut;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(customerPortOut, tokenPortOut, notificationPortOut, identityProviderPortOut,
                Duration.ofHours(24), FRONTEND_BASE_URL);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private static String sha256Hex(String value) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static Customer unverifiedCustomer() {
        return new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
    }

    private static Customer verifiedCustomer() {
        var customer = unverifiedCustomer();
        customer.markEmailVerified();
        return customer;
    }

    private static EmailVerificationToken storedToken(UUID customerId, Instant expiresAt, Instant usedAt) throws Exception {
        return new EmailVerificationToken(UUID.randomUUID(), customerId, sha256Hex(RAW_TOKEN),
                Instant.now().minus(Duration.ofHours(1)), expiresAt, usedAt);
    }

    private static String tokenFromLink(String link) {
        return link.substring(LINK_PREFIX.length());
    }

    private EmailVerificationToken captureSavedToken() {
        var captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenPortOut).save(captor.capture());
        return captor.getValue();
    }

    private String captureSentLink(Customer customer) {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(notificationPortOut).sendEmailVerification(eq(customer.getEmail()), eq(customer.getName()), captor.capture());
        return captor.getValue();
    }

    @Nested
    class IssueVerification {

        @Test
        @DisplayName("Deve salvar o hash SHA-256 do token do link e nunca o token cru quando emitir verificação")
        void shouldSaveOnlyTokenHashMatchingLinkTokenWhenIssuingVerification() throws Exception {
            var customer = unverifiedCustomer();

            service.issueVerification(customer);

            var saved = captureSavedToken();
            var linkToken = tokenFromLink(captureSentLink(customer));
            assertThat(linkToken).isNotBlank();
            assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(linkToken));
            assertThat(saved.getTokenHash()).isNotEqualTo(linkToken);
            assertThat(saved.getTokenHash()).hasSize(64);
            assertThat(saved.getCustomerId()).isEqualTo(customer.getId());
            assertThat(saved.getUsedAt()).isNull();
        }

        @Test
        @DisplayName("Deve enviar o e-mail ao cliente com o link do frontend quando emitir verificação")
        void shouldSendEmailToCustomerWithFrontendLinkWhenIssuingVerification() {
            var customer = unverifiedCustomer();

            service.issueVerification(customer);

            assertThat(captureSentLink(customer)).startsWith(LINK_PREFIX);
        }

        @Test
        @DisplayName("Deve definir a validade em 24 horas a partir da criação quando emitir verificação")
        void shouldSetExpirationTwentyFourHoursAfterCreationWhenIssuingVerification() {
            var before = Instant.now();

            service.issueVerification(unverifiedCustomer());

            var after = Instant.now();
            var saved = captureSavedToken();
            assertThat(saved.getCreatedAt()).isBetween(before, after);
            assertThat(Duration.between(saved.getCreatedAt(), saved.getExpiresAt())).isEqualTo(Duration.ofHours(24));
        }

        @Test
        @DisplayName("Deve gerar tokens diferentes a cada emissão")
        void shouldGenerateDifferentTokensOnEachIssuance() {
            var customer = unverifiedCustomer();

            service.issueVerification(customer);
            service.issueVerification(customer);

            var captor = ArgumentCaptor.forClass(String.class);
            verify(notificationPortOut, times(2))
                    .sendEmailVerification(eq(customer.getEmail()), eq(customer.getName()), captor.capture());
            assertThat(captor.getAllValues()).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Deve remover a barra final do frontend-base-url ao montar o link")
        void shouldStripTrailingSlashFromFrontendBaseUrlWhenBuildingLink() {
            var slashService = new EmailVerificationService(customerPortOut, tokenPortOut, notificationPortOut, identityProviderPortOut,
                    Duration.ofHours(24), FRONTEND_BASE_URL + "/");
            var customer = unverifiedCustomer();

            slashService.issueVerification(customer);

            assertThat(captureSentLink(customer)).startsWith(LINK_PREFIX).doesNotContain("//verify-email");
        }

        @Test
        @DisplayName("Deve gravar o token já e enviar o e-mail somente após o commit quando houver transação")
        void shouldSaveTokenNowAndSendEmailOnlyAfterCommitWhenInTransaction() {
            var customer = unverifiedCustomer();
            TransactionSynchronizationManager.initSynchronization();

            service.issueVerification(customer);

            then(tokenPortOut).should().save(any(EmailVerificationToken.class));
            then(notificationPortOut).shouldHaveNoInteractions();
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertThat(captureSentLink(customer)).startsWith(LINK_PREFIX);
        }

        @Test
        @DisplayName("Deve não enviar o e-mail quando a transação sofrer rollback")
        void shouldNotSendEmailWhenTransactionRollsBack() {
            TransactionSynchronizationManager.initSynchronization();

            service.issueVerification(unverifiedCustomer());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            then(notificationPortOut).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Deve salvar o token antes de enviar o e-mail")
        void shouldSaveTokenBeforeSendingEmail() {
            var customer = unverifiedCustomer();

            service.issueVerification(customer);

            var order = inOrder(tokenPortOut, notificationPortOut);
            order.verify(tokenPortOut).save(any(EmailVerificationToken.class));
            order.verify(notificationPortOut).sendEmailVerification(anyString(), anyString(), anyString());
        }
    }

    @Nested
    class VerifyEmail {

        @Test
        @DisplayName("Deve marcar o cliente como verificado e salvar quando o token for válido")
        void shouldMarkCustomerVerifiedAndSaveWhenTokenIsValid() throws Exception {
            var customer = unverifiedCustomer();
            var token = storedToken(customer.getId(), Instant.now().plus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).thenReturn(true);
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            service.verifyEmail(RAW_TOKEN);

            assertThat(customer.hasVerifiedEmail()).isTrue();
            assertThat(customer.getEmailVerified()).isTrue();
            verify(customerPortOut).save(customer);
        }

        @Test
        @DisplayName("Deve consumir o token com o instante atual quando o token for válido")
        void shouldConsumeTokenWithCurrentInstantWhenTokenIsValid() throws Exception {
            var customer = unverifiedCustomer();
            var token = storedToken(customer.getId(), Instant.now().plus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).thenReturn(true);
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));
            var before = Instant.now();

            service.verifyEmail(RAW_TOKEN);

            var captor = ArgumentCaptor.forClass(Instant.class);
            verify(tokenPortOut).markUsed(eq(token.getId()), captor.capture());
            assertThat(captor.getValue()).isBetween(before, Instant.now());
        }

        @Test
        @DisplayName("Deve não salvar de novo quando o cliente já estiver verificado")
        void shouldNotSaveAgainWhenCustomerIsAlreadyVerified() throws Exception {
            var customer = verifiedCustomer();
            var token = storedToken(customer.getId(), Instant.now().plus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).thenReturn(true);
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            service.verifyEmail(RAW_TOKEN);

            assertThat(customer.hasVerifiedEmail()).isTrue();
            verify(customerPortOut, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar token inválido quando o token não existir")
        void shouldThrowInvalidTokenWhenTokenDoesNotExist() throws Exception {
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessage(EmailVerificationService.INVALID_TOKEN_MESSAGE);

            verify(tokenPortOut, never()).markUsed(any(), any());
            verifyNoInteractions(customerPortOut);
        }

        @Test
        @DisplayName("Deve lançar token já usado quando o token já tiver sido consumido")
        void shouldThrowUsedTokenWhenTokenWasAlreadyUsed() throws Exception {
            var token = storedToken(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)), Instant.now().minusSeconds(60));
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessage(EmailVerificationService.USED_TOKEN_MESSAGE);

            verify(tokenPortOut, never()).markUsed(any(), any());
            verifyNoInteractions(customerPortOut);
        }

        @Test
        @DisplayName("Deve lançar token expirado quando o token estiver vencido")
        void shouldThrowExpiredTokenWhenTokenIsExpired() throws Exception {
            var token = storedToken(UUID.randomUUID(), Instant.now().minus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessage(EmailVerificationService.EXPIRED_TOKEN_MESSAGE);

            verify(tokenPortOut, never()).markUsed(any(), any());
            verifyNoInteractions(customerPortOut);
        }

        @Test
        @DisplayName("Deve lançar token já usado quando outra requisição consumir o token primeiro")
        void shouldThrowUsedTokenWhenAnotherRequestConsumesTokenFirst() throws Exception {
            var token = storedToken(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).thenReturn(false);

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessage(EmailVerificationService.USED_TOKEN_MESSAGE);

            verifyNoInteractions(customerPortOut);
        }

        @Test
        @DisplayName("Deve lançar token inválido quando o cliente do token não existir")
        void shouldThrowInvalidTokenWhenCustomerDoesNotExist() throws Exception {
            var customerId = UUID.randomUUID();
            var token = storedToken(customerId, Instant.now().plus(Duration.ofHours(1)), null);
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.of(token));
            when(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).thenReturn(true);
            when(customerPortOut.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                    .isInstanceOf(InvalidVerificationTokenException.class)
                    .hasMessage(EmailVerificationService.INVALID_TOKEN_MESSAGE);

            verify(customerPortOut, never()).save(any());
        }

        @Test
        @DisplayName("Deve buscar o token pelo hash SHA-256 do valor recebido")
        void shouldLookUpTokenBySha256HashOfReceivedValue() throws Exception {
            when(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN)).isInstanceOf(InvalidVerificationTokenException.class);

            verify(tokenPortOut).findByTokenHash(sha256Hex(RAW_TOKEN));
        }
    }

    @Nested
    class ResendVerification {

        @Test
        @DisplayName("Deve invalidar os tokens antigos e emitir novo token e e-mail quando o cliente não estiver verificado")
        void shouldDeleteUnusedTokensAndIssueNewWhenCustomerIsNotVerified() {
            var customer = unverifiedCustomer();
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

            service.resendVerification(customer.getEmail());

            var order = inOrder(tokenPortOut, notificationPortOut);
            order.verify(tokenPortOut).deleteUnusedByCustomerId(customer.getId());
            order.verify(tokenPortOut).save(any(EmailVerificationToken.class));
            order.verify(notificationPortOut).sendEmailVerification(eq(customer.getEmail()), eq(customer.getName()), anyString());
        }

        @Test
        @DisplayName("Deve não fazer nada quando o cliente já estiver verificado")
        void shouldDoNothingWhenCustomerIsAlreadyVerified() {
            var customer = verifiedCustomer();
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

            service.resendVerification(customer.getEmail());

            verifyNoInteractions(tokenPortOut, notificationPortOut);
        }

        @Test
        @DisplayName("Deve não fazer nada e não lançar exceção quando o e-mail não existir")
        void shouldDoNothingAndNotThrowWhenEmailDoesNotExist() {
            when(customerPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());

            service.resendVerification("ninguem@mail.com");

            verifyNoInteractions(tokenPortOut, notificationPortOut);
        }
    }

    @Test
    @DisplayName("Deve marcar o e-mail como verificado no IdP quando a verificação for concluída")
    void shouldMarkEmailVerifiedInIdentityProviderWhenVerificationSucceeds() throws Exception {
        var customer = unverifiedCustomer();
        var token = storedToken(customer.getId(), Instant.now().plus(Duration.ofHours(1)), null);
        given(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).willReturn(Optional.of(token));
        given(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).willReturn(true);
        given(customerPortOut.findById(customer.getId())).willReturn(Optional.of(customer));

        service.verifyEmail(RAW_TOKEN);

        then(identityProviderPortOut).should().markEmailVerified(customer.getId());
    }

    @Test
    @DisplayName("Deve propagar a exceção quando o IdP falhar ao marcar o e-mail como verificado")
    void shouldPropagateExceptionWhenIdentityProviderFailsToMarkEmailVerified() throws Exception {
        var customer = unverifiedCustomer();
        var token = storedToken(customer.getId(), Instant.now().plus(Duration.ofHours(1)), null);
        given(tokenPortOut.findByTokenHash(sha256Hex(RAW_TOKEN))).willReturn(Optional.of(token));
        given(tokenPortOut.markUsed(eq(token.getId()), any(Instant.class))).willReturn(true);
        given(customerPortOut.findById(customer.getId())).willReturn(Optional.of(customer));
        willThrow(new DependencyUnavailableException("keycloak", new IllegalStateException("fora")))
                .given(identityProviderPortOut).markEmailVerified(customer.getId());

        assertThatThrownBy(() -> service.verifyEmail(RAW_TOKEN))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
