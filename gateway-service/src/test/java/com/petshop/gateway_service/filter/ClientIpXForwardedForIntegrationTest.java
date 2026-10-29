package com.petshop.gateway_service.filter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SDD-028: o gateway, como borda, entrega ao downstream X-Forwarded-For com
 * só o IP do peer TCP — o que o cliente forjar nunca chega lá.
 *
 * <p>Downstream falso: o HttpServer do próprio JDK numa porta aleatória (sem
 * dependência nova e sem conflito de Jetty/OkHttp com o Boot 4), guardando os
 * headers da última requisição. O IP do cliente é simulado decorando o
 * request num WebFilter do WebTestClient ligado ao contexto; um teste extra
 * passa pelo Netty de verdade (porta aleatória, peer 127.0.0.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientIpXForwardedForIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String ROUTED_PATH = "/api/v1/customers/xff-probe";

    private static final AtomicReference<Headers> lastDownstreamHeaders = new AtomicReference<>();
    private static final HttpServer downstream = startDownstream();

    @Autowired
    private ApplicationContext context;

    @Value("${local.server.port}")
    private int gatewayPort;

    @DynamicPropertySource
    static void routeCustomersToFakeDownstream(DynamicPropertyRegistry registry) {
        registry.add("GATEWAY_CUSTOMERS_URL",
                () -> "http://127.0.0.1:" + downstream.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop(0);
    }

    @BeforeEach
    void clearCapturedHeaders() {
        lastDownstreamHeaders.set(null);
    }

    @Test
    void ca1_semHeader_downstreamRecebeSoOIpDoCliente() {
        clientFrom(CLIENT_IP).get().uri(ROUTED_PATH)
                .exchange()
                .expectStatus().isOk();

        assertThat(downstreamXForwardedFor()).containsExactly(CLIENT_IP);
    }

    @Test
    void ca2_comXffDoCliente_ultimoElementoEhOIpDoPeer() {
        clientFrom(CLIENT_IP).get().uri(ROUTED_PATH)
                .header("X-Forwarded-For", "1.2.3.4")
                .exchange()
                .expectStatus().isOk();

        var chain = downstreamXForwardedFor();
        assertThat(lastElement(chain)).isEqualTo(CLIENT_IP);
        // RF2 (preferência): o recebido é descartado, não só empurrado à esquerda.
        assertThat(chain).containsExactly(CLIENT_IP);
    }

    @Test
    void xffForjadoComVariasEntradasEVariasLinhas_eDescartadoPorInteiro() {
        clientFrom(CLIENT_IP).get().uri(ROUTED_PATH)
                .header("X-Forwarded-For", "1.2.3.4, 172.28.0.10, 198.51.100.99")
                .header("X-Forwarded-For", "10.0.0.1")
                .header("x-forwarded-for", "192.0.2.55")
                .header("Forwarded", "for=6.6.6.6;proto=https")
                .header("X-Forwarded-Host", "evil.example")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectStatus().isOk();

        var headers = lastDownstreamHeaders.get();
        assertThat(downstreamXForwardedFor()).containsExactly(CLIENT_IP);
        assertThat(headers.get("Forwarded")).isNull();
        assertThat(headers.get("X-Forwarded-Host")).isNull();
        assertThat(headers.get("X-Forwarded-Proto")).isNull();
    }

    @Test
    void clienteDentroDaRedeDoDocker_naoControlaOUltimoElemento() {
        // Mesmo que o IP do peer caia numa allowlist ampla do downstream
        // (ex.: 172.16.0.0/12), não há nada à esquerda para ele "promover".
        var dockerPeer = "172.28.0.50";
        clientFrom(dockerPeer).get().uri(ROUTED_PATH)
                .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                .exchange()
                .expectStatus().isOk();

        assertThat(downstreamXForwardedFor()).containsExactly(dockerPeer);
    }

    @Test
    void peerIpv6_eRepassadoComoLiteral() {
        clientFrom("2001:db8::7").get().uri(ROUTED_PATH)
                .header("X-Forwarded-For", "1.2.3.4")
                .exchange()
                .expectStatus().isOk();

        assertThat(downstreamXForwardedFor()).containsExactly("2001:db8:0:0:0:0:0:7");
    }

    @Test
    void viaNettyDeVerdade_xffForjadoNaoTrocaOPeer() {
        WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + gatewayPort)
                .build()
                .get().uri(ROUTED_PATH)
                .header("X-Forwarded-For", "1.2.3.4")
                .header("Forwarded", "for=6.6.6.6")
                .exchange()
                .expectStatus().isOk();

        assertThat(downstreamXForwardedFor()).containsExactly("127.0.0.1");
    }

    private WebTestClient clientFrom(String ip) {
        WebFilter fakePeer = (exchange, chain) -> {
            var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public InetSocketAddress getRemoteAddress() {
                    return new InetSocketAddress(literal(ip), 54321);
                }
            };
            return chain.filter(exchange.mutate().request(request).build());
        };
        return WebTestClient.bindToApplicationContext(context)
                .webFilter(fakePeer)
                .configureClient()
                .build();
    }

    private static InetAddress literal(String ip) {
        try {
            return InetAddress.getByName(ip); // literal: não faz lookup de DNS
        } catch (IOException e) {
            throw new IllegalArgumentException(ip, e);
        }
    }

    private static List<String> downstreamXForwardedFor() {
        var headers = lastDownstreamHeaders.get();
        assertThat(headers).as("downstream não recebeu a requisição").isNotNull();
        var lines = headers.get("X-Forwarded-For");
        assertThat(lines).as("downstream não recebeu X-Forwarded-For").isNotNull();
        return lines.stream()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::trim)
                .toList();
    }

    private static String lastElement(List<String> chain) {
        return chain.get(chain.size() - 1);
    }

    private static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                lastDownstreamHeaders.set(exchange.getRequestHeaders());
                var body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o downstream falso", e);
        }
    }
}
