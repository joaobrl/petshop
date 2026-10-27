package com.petshop.notification_service;

import com.petshop.notification_service.adapter.out.brevo.BrevoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(BrevoProperties.class)
public class NotificationServiceApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

}
