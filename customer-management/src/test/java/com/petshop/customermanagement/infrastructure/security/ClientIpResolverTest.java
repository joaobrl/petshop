package com.petshop.customermanagement.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static final String GATEWAY_IP = "172.18.0.5";

    private final ClientIpResolver resolver = new ClientIpResolver(List.of("172.16.0.0/12", " fd00::/8 ", ""));

    @Test
    void directClientWithoutProxyUsesRemoteAddr() {
        var request = request("203.0.113.7");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void untrustedClientForgingXForwardedForIsIgnored() {
        var request = request("203.0.113.7", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void trustedProxyUsesTheForwardedClientIp() {
        var request = request(GATEWAY_IP, "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void trustedProxyWithoutXForwardedForFallsBackToRemoteAddr() {
        assertThat(resolver.resolve(request(GATEWAY_IP))).isEqualTo(GATEWAY_IP);
        assertThat(resolver.resolve(request(GATEWAY_IP, "   "))).isEqualTo(GATEWAY_IP);
    }

    @Test
    void multiHopChainUsesTheRightmostUntrustedIp() {
        // Cliente forjou "6.6.6.6" à esquerda; o gateway anotou o IP real
        // (1.1.1.1) e um proxy interno confiável anotou o gateway.
        var request = request(GATEWAY_IP, "6.6.6.6, 1.1.1.1, 172.20.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void multipleXForwardedForHeaderLinesAreReadAsOneChain() {
        var request = request(GATEWAY_IP, "6.6.6.6", "1.1.1.1, 172.20.0.9");

        assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
    }

    @Test
    void chainMadeOnlyOfTrustedProxiesFallsBackToRemoteAddr() {
        var request = request(GATEWAY_IP, "172.20.0.9, 172.21.0.3");

        assertThat(resolver.resolve(request)).isEqualTo(GATEWAY_IP);
    }

    @Test
    void ipv6ClientBehindIpv6TrustedProxy() {
        var request = request("fd00::1", "2001:db8::42");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::42");
    }

    @Test
    void untrustedIpv6ClientIgnoresXForwardedFor() {
        var request = request("2001:db8::1", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-ip", "evil.example.com", "999.1.1.1", "1.2.3", "1.1.1.1:8080", "[2001:db8::1]", "2001:db8::zz", ""})
    void malformedRightmostHopFallsBackToRemoteAddr(String hop) {
        var request = request(GATEWAY_IP, "1.1.1.1, " + hop);

        assertThat(resolver.resolve(request)).isEqualTo(GATEWAY_IP);
    }

    @Test
    void emptyAllowlistTrustsNobody() {
        var noProxies = new ClientIpResolver(List.of());

        assertThat(noProxies.resolve(request(GATEWAY_IP, "1.1.1.1"))).isEqualTo(GATEWAY_IP);
        assertThat(new ClientIpResolver(null).resolve(request(GATEWAY_IP, "1.1.1.1"))).isEqualTo(GATEWAY_IP);
    }

    @Test
    void invalidCidrFailsFastAtStartup() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("10.0.0.0/99")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest request(String remoteAddr, String... xForwardedFor) {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        Arrays.stream(xForwardedFor).forEach(value -> request.addHeader(ClientIpResolver.X_FORWARDED_FOR, value));
        return request;
    }
}
