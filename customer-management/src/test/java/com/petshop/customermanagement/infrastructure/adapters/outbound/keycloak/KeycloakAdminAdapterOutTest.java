package com.petshop.customermanagement.infrastructure.adapters.outbound.keycloak;

import ch.qos.logback.classic.Level;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.DependencyUnavailableException;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminAdapterOutTest {

    private static final String BASE = "http://keycloak.test/admin/realms/petshop";
    private static final String AUTHORIZATION = "Bearer service-token";
    private static final String IDP_ID = "kc-1";
    private static final String WEB_CLIENT_ID = "web";
    private static final String REDIRECT_URI = "http://front.test/";

    private final UUID userId = UUID.randomUUID();

    private MockRestServiceServer server;
    private KeycloakAdminAdapterOut adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new KeycloakAdminAdapterOut(builder.build(), () -> AUTHORIZATION, WEB_CLIENT_ID, REDIRECT_URI);
    }

    private IdentityUser user(String name, Role role) {
        return new IdentityUser(userId, "Maria@Mail.com", name, "12345678900", "11999999999", role,
                role == Role.CUSTOMER ? AccountType.CUSTOMER : AccountType.STAFF, false);
    }

    private static RequestMatcher query(String... fragments) {
        return request -> assertThat(request.getURI().getQuery()).contains(fragments);
    }

    private void expectCreatedWithoutLocation() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    private void expectCreatedWithLocation() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withCreatedEntity(URI.create(BASE + "/users/" + IDP_ID)));
    }

    private void expectCurrentRoles(String json) {
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectAvailableRoles(String json) {
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm/available"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectRoleAssignment(Role role) {
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].name").value(role.name()))
                .andRespond(withNoContent());
    }

    private void expectFullRoleAssignment(Role role) {
        expectCurrentRoles("[]");
        expectAvailableRoles("[{\"id\":\"r-1\",\"name\":\"" + role.name() + "\"}]");
        expectRoleAssignment(role);
    }

    private void expectLookupByUserId(String json) {
        server.expect(requestTo(startsWith(BASE + "/users?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(query("q=user_id:" + userId, "exact=true"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectFoundByUserId() {
        expectLookupByUserId("[{\"id\":\"" + IDP_ID + "\",\"attributes\":{\"user_id\":[\"" + userId + "\"]}}]");
    }

    private void expectGetUser() {
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + IDP_ID + "\",\"enabled\":true,\"attributes\":{\"locale\":[\"pt\"],"
                        + "\"user_id\":[\"" + userId + "\"]}}", MediaType.APPLICATION_JSON));
    }

    private void expectPutUser() {
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withNoContent());
    }

    private void expectDelete(boolean succeeds) {
        var request = server.expect(requestTo(BASE + "/users/" + IDP_ID)).andExpect(method(HttpMethod.DELETE));
        request.andRespond(succeeds ? withNoContent() : withServerError());
    }

    @Test
    @DisplayName("Deve criar o usuário com senha não temporária e atribuir o papel quando a criação retornar 201 com Location")
    void shouldCreateUserWithPermanentPasswordAndAssignRoleWhenResponseIs201WithLocation() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andExpect(jsonPath("$.username").value("maria@mail.com"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.email").value("Maria@Mail.com"))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Silva"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.attributes.user_id[0]").value(userId.toString()))
                .andExpect(jsonPath("$.attributes.cpf[0]").value("12345678900"))
                .andExpect(jsonPath("$.attributes.phone[0]").value("11999999999"))
                .andExpect(jsonPath("$.attributes.type[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.credentials[0].type").value("password"))
                .andExpect(jsonPath("$.credentials[0].value").value("Senha123"))
                .andExpect(jsonPath("$.credentials[0].temporary").value(false))
                .andRespond(withCreatedEntity(URI.create(BASE + "/users/" + IDP_ID)));
        expectFullRoleAssignment(Role.CUSTOMER);

        var result = adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123");

        assertThat(result).isEqualTo(IDP_ID);
        server.verify();
    }

    @Test
    @DisplayName("Deve não enviar credencial quando a senha for nula")
    void shouldNotSendCredentialWhenPasswordIsNull() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.credentials").doesNotExist())
                .andRespond(withCreatedEntity(URI.create(BASE + "/users/" + IDP_ID)));
        expectFullRoleAssignment(Role.RECEPTIONIST);

        var result = adapter.createUser(user("Maria Silva", Role.RECEPTIONIST), null);

        assertThat(result).isEqualTo(IDP_ID);
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar ConflictException quando o Keycloak responder 409")
    void shouldThrowConflictExceptionWhenKeycloakRespondsConflict() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email já cadastrado");
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar BusinessRuleException quando o Keycloak responder 400")
    void shouldThrowBusinessRuleExceptionWhenKeycloakRespondsBadRequest() {
        server.expect(requestTo(BASE + "/users")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(BusinessRuleException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando a criação retornar 201 sem Location")
    void shouldThrowDependencyUnavailableExceptionWhenCreatedResponseHasNoLocation() {
        expectCreatedWithoutLocation();

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve apagar o usuário criado e repassar a falha quando a atribuição do papel falhar")
    void shouldDeleteCreatedUserAndRethrowWhenRoleAssignmentFails() {
        expectCreatedWithLocation();
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        expectDelete(true);

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve apagar o usuário criado e repassar a falha quando o papel não existir no Keycloak")
    void shouldDeleteCreatedUserAndRethrowWhenRoleIsMissingInKeycloak() {
        expectCreatedWithLocation();
        expectCurrentRoles("[]");
        expectAvailableRoles("[{\"id\":\"r-9\",\"name\":\"ADMIN\"}]");
        expectDelete(true);

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasRootCauseMessage("Realm role ausente no Keycloak: CUSTOMER");
        server.verify();
    }

    @Test
    @DisplayName("Deve logar o erro e repassar a falha original quando a remoção do usuário criado também falhar")
    void shouldLogErrorAndRethrowOriginalFailureWhenCompensationDeletionAlsoFails() {
        expectCreatedWithLocation();
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        expectDelete(false);

        try (var logs = LogCaptor.of(KeycloakAdminAdapterOut.class)) {
            assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                    .isInstanceOf(BusinessRuleException.class);

            assertThat(logs.has(Level.ERROR, "Could not remove identity provider user")).isTrue();
        }
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando o Keycloak responder 5xx")
    void shouldThrowDependencyUnavailableExceptionWhenKeycloakRespondsServerError() {
        server.expect(requestTo(BASE + "/users")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando o Keycloak estiver indisponível")
    void shouldThrowDependencyUnavailableExceptionWhenKeycloakIsUnreachable() {
        server.expect(requestTo(BASE + "/users")).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> adapter.createUser(user("Maria Silva", Role.CUSTOMER), "Senha123"))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar o id quando encontrar o usuário pelo user_id")
    void shouldReturnIdWhenUserIsFoundByUserId() {
        server.expect(requestTo(startsWith(BASE + "/users?")))
                .andExpect(query("q=user_id:" + userId, "exact=true", "briefRepresentation=false"))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andRespond(withSuccess("[{\"id\":\"" + IDP_ID + "\",\"attributes\":{\"user_id\":[\""
                        + userId.toString().toUpperCase() + "\"]}}]", MediaType.APPLICATION_JSON));

        var result = adapter.findIdByUserId(userId);

        assertThat(result).contains(IDP_ID);
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar o id quando encontrar o usuário pelo e-mail ignorando maiúsculas")
    void shouldReturnIdWhenUserIsFoundByEmailIgnoringCase() {
        server.expect(requestTo(startsWith(BASE + "/users?")))
                .andExpect(query("email=maria@mail.com", "exact=true"))
                .andRespond(withSuccess("[{\"id\":\"" + IDP_ID + "\",\"email\":\"Maria@Mail.com\"}]",
                        MediaType.APPLICATION_JSON));

        var result = adapter.findIdByEmail("maria@mail.com");

        assertThat(result).contains(IDP_ID);
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio quando a busca por user_id devolver lista vazia ou nula")
    void shouldReturnEmptyWhenSearchByUserIdReturnsEmptyOrNullBody() {
        expectLookupByUserId("[]");
        expectLookupByUserId("");

        assertThat(adapter.findIdByUserId(userId)).isEmpty();
        assertThat(adapter.findIdByUserId(userId)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio quando a busca por e-mail devolver lista vazia ou nula")
    void shouldReturnEmptyWhenSearchByEmailReturnsEmptyOrNullBody() {
        server.expect(requestTo(startsWith(BASE + "/users?"))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/users?"))).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(adapter.findIdByEmail("maria@mail.com")).isEmpty();
        assertThat(adapter.findIdByEmail("maria@mail.com")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve ignorar usuários com atributo user_id diferente ou ausente quando buscar por user_id")
    void shouldIgnoreUsersWithDifferentOrMissingUserIdAttributeWhenSearchingByUserId() {
        expectLookupByUserId("[{\"id\":\"other\",\"attributes\":{\"user_id\":[\"" + UUID.randomUUID() + "\"]}},"
                + "{\"id\":\"no-attr\"},{\"id\":\"empty\",\"attributes\":{\"user_id\":[]}}]");

        var result = adapter.findIdByUserId(userId);

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Deve sincronizar nome com sobrenome, e-mail, telefone e papel quando atualizar o usuário")
    void shouldSyncNameWithSurnameEmailPhoneAndRoleWhenUpdatingUser() {
        expectFoundByUserId();
        expectGetUser();
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Silva Souza"))
                .andExpect(jsonPath("$.email").value("novo@mail.com"))
                .andExpect(jsonPath("$.attributes.phone[0]").value("11888888888"))
                .andExpect(jsonPath("$.attributes.locale[0]").value("pt"))
                .andExpect(jsonPath("$.attributes.type[0]").value("STAFF"))
                .andRespond(withNoContent());
        expectFullRoleAssignment(Role.ADMIN);
        var updated = new IdentityUser(userId, "novo@mail.com", "Maria  Silva Souza ", "12345678900", "11888888888",
                Role.ADMIN, AccountType.STAFF, true);

        adapter.updateUser(updated);

        server.verify();
    }

    @Test
    @DisplayName("Deve enviar sobrenome nulo quando o nome tiver uma palavra só ao atualizar o usuário")
    void shouldSendNullLastNameWhenNameHasSingleWordWhenUpdatingUser() {
        expectFoundByUserId();
        expectGetUser();
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value(nullValue()))
                .andRespond(withNoContent());
        expectCurrentRoles("[{\"id\":\"r-1\",\"name\":\"CUSTOMER\"}]");

        adapter.updateUser(user("Maria", Role.CUSTOMER));

        server.verify();
    }

    @Test
    @DisplayName("Deve apenas logar aviso e não lançar exceção quando atualizar usuário inexistente no IdP")
    void shouldOnlyLogWarningWithoutExceptionWhenUpdatingUserThatDoesNotExistInIdp() {
        expectLookupByUserId("[]");

        try (var logs = LogCaptor.of(KeycloakAdminAdapterOut.class)) {
            assertThatCode(() -> adapter.updateUser(user("Maria Silva", Role.CUSTOMER))).doesNotThrowAnyException();

            assertThat(logs.has(Level.WARN, "user not found for update")).isTrue();
        }
        server.verify();
    }

    @Test
    @DisplayName("Deve não alterar papéis quando o papel já estiver atribuído")
    void shouldNotChangeRolesWhenRoleIsAlreadyAssigned() {
        expectFoundByUserId();
        expectGetUser();
        expectPutUser();
        expectCurrentRoles("[{\"id\":\"r-1\",\"name\":\"CUSTOMER\"},{\"id\":\"r-2\",\"name\":\"offline_access\"}]");

        adapter.updateUser(user("Maria Silva", Role.CUSTOMER));

        server.verify();
    }

    @Test
    @DisplayName("Deve remover o papel antigo da aplicação e atribuir o novo quando o papel mudar")
    void shouldRemoveStaleApplicationRoleAndAssignNewOneWhenRoleChanges() {
        expectFoundByUserId();
        expectGetUser();
        expectPutUser();
        expectCurrentRoles("[{\"id\":\"r-1\",\"name\":\"RECEPTIONIST\"},{\"id\":\"r-2\",\"name\":\"offline_access\"}]");
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("RECEPTIONIST"))
                .andRespond(withNoContent());
        expectAvailableRoles("[{\"id\":\"r-3\",\"name\":\"ADMIN\"}]");
        expectRoleAssignment(Role.ADMIN);

        adapter.updateUser(user("Maria Silva", Role.ADMIN));

        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando o papel estiver ausente nos disponíveis")
    void shouldThrowDependencyUnavailableExceptionWhenRoleIsMissingFromAvailableRoles() {
        expectFoundByUserId();
        expectGetUser();
        expectPutUser();
        expectCurrentRoles("[]");
        expectAvailableRoles("[{\"id\":\"r-3\",\"name\":\"GROOMER\"}]");

        assertThatThrownBy(() -> adapter.updateUser(user("Maria Silva", Role.ADMIN)))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve desabilitar o usuário e encerrar as sessões quando desativar")
    void shouldDisableUserAndLogoutSessionsWhenDisabling() {
        expectFoundByUserId();
        expectGetUser();
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(false))
                .andRespond(withNoContent());
        server.expect(requestTo(BASE + "/users/" + IDP_ID + "/logout"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        adapter.setEnabled(userId, false);

        server.verify();
    }

    @Test
    @DisplayName("Deve reabilitar o usuário sem encerrar sessões quando reativar")
    void shouldEnableUserWithoutLogoutWhenEnabling() {
        expectFoundByUserId();
        expectGetUser();
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.enabled").value(true))
                .andRespond(withNoContent());

        adapter.setEnabled(userId, true);

        server.verify();
    }

    @Test
    @DisplayName("Deve apenas logar aviso quando habilitar ou desabilitar usuário inexistente no IdP")
    void shouldOnlyLogWarningWhenTogglingUserThatDoesNotExistInIdp() {
        expectLookupByUserId("[]");

        try (var logs = LogCaptor.of(KeycloakAdminAdapterOut.class)) {
            assertThatCode(() -> adapter.setEnabled(userId, false)).doesNotThrowAnyException();

            assertThat(logs.has(Level.WARN, "user not found for disable")).isTrue();
        }
        server.verify();
    }

    @Test
    @DisplayName("Deve definir emailVerified como verdadeiro quando marcar o e-mail como verificado")
    void shouldSetEmailVerifiedTrueWhenMarkingEmailVerified() {
        expectFoundByUserId();
        expectGetUser();
        server.expect(requestTo(BASE + "/users/" + IDP_ID))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andRespond(withNoContent());

        adapter.markEmailVerified(userId);

        server.verify();
    }

    @Test
    @DisplayName("Deve apenas logar aviso quando marcar e-mail verificado de usuário inexistente no IdP")
    void shouldOnlyLogWarningWhenMarkingEmailVerifiedOfUserThatDoesNotExistInIdp() {
        expectLookupByUserId("[]");

        try (var logs = LogCaptor.of(KeycloakAdminAdapterOut.class)) {
            assertThatCode(() -> adapter.markEmailVerified(userId)).doesNotThrowAnyException();

            assertThat(logs.has(Level.WARN, "user not found for mark email verified")).isTrue();
        }
        server.verify();
    }

    @Test
    @DisplayName("Deve solicitar o e-mail de ação UPDATE_PASSWORD com client_id e redirect_uri quando enviar e-mail de senha")
    void shouldRequestUpdatePasswordActionEmailWithClientIdAndRedirectUriWhenSendingSetPasswordEmail() {
        server.expect(requestTo(startsWith(BASE + "/users/" + IDP_ID + "/execute-actions-email?")))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(query("client_id=" + WEB_CLIENT_ID, "redirect_uri=" + REDIRECT_URI))
                .andExpect(jsonPath("$[0]").value("UPDATE_PASSWORD"))
                .andRespond(withNoContent());

        adapter.sendSetPasswordEmail(IDP_ID);

        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando o envio do e-mail de senha falhar")
    void shouldThrowDependencyUnavailableExceptionWhenSetPasswordEmailFails() {
        server.expect(requestTo(startsWith(BASE + "/users/" + IDP_ID + "/execute-actions-email")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.sendSetPasswordEmail(IDP_ID))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("Deve remover o usuário quando o Keycloak responder sucesso")
    void shouldDeleteUserWhenKeycloakRespondsSuccess() {
        expectDelete(true);

        assertThatCode(() -> adapter.deleteUser(IDP_ID)).doesNotThrowAnyException();

        server.verify();
    }

    @Test
    @DisplayName("Deve lançar DependencyUnavailableException quando remover usuário que o Keycloak responde 404")
    void shouldThrowDependencyUnavailableExceptionWhenDeletingUserAndKeycloakRespondsNotFound() {
        server.expect(requestTo(BASE + "/users/" + IDP_ID)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.deleteUser(IDP_ID))
                .isInstanceOf(DependencyUnavailableException.class);
        server.verify();
    }
}
