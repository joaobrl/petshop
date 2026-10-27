package com.petshop.gateway_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void declaraUmaRotaParaCadaMicrosservicoDownstream(@org.springframework.beans.factory.annotation.Autowired RouteLocator routeLocator) {
		var routeIds = routeLocator.getRoutes()
				.map(route -> route.getId())
				.collectList()
				.block();

		assertThat(routeIds).containsExactlyInAnyOrder(
				"customer-management",
				"product-registration",
				"staff-service",
				"booking-service",
				"order-service"
		);
	}

	@Test
	void routeLocatorPublicaAsRotasSemErro(@org.springframework.beans.factory.annotation.Autowired RouteLocator routeLocator) {
		StepVerifier.create(routeLocator.getRoutes()).expectNextCount(5).verifyComplete();
	}

}
