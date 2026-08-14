package com.petshop.product_registration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// MongoTemplate conecta de verdade já na subida do contexto (server
// selection eager, independente de auto-index-creation) — sem um Mongo
// real alcançável, a criação do bean trava até o timeout do driver.
@SpringBootTest
@Testcontainers
class ProductRegistrationApplicationTests {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

	@Test
	void contextLoads() {
	}

}
