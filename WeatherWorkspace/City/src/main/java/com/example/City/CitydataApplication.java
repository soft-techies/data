package com.example.City;

import java.util.ArrayList;
import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@Configuration
@EnableAutoConfiguration
@SpringBootApplication
@EntityScan(basePackages = "com.example.City.model")
@EnableJpaRepositories(basePackages = "com.example.City.Repository")
public class CitydataApplication {

	public static void main(String[] args) {
		SpringApplication.run(CitydataApplication.class, args);
	}
	@Bean
	public Docket swaggerConfig() {
		Parameter authHeader = new ParameterBuilder().parameterType("header").name("Authorization")
				.modelRef(new ModelRef("string")).build();

		return new Docket(DocumentationType.SWAGGER_2).select()
				.apis(RequestHandlerSelectors.basePackage("com.example.City.Controllers")).build()
				.apiInfo(swaggerMetaData()).globalOperationParameters(Collections.singletonList(authHeader));
	}
	
	private ApiInfo swaggerMetaData() {
		return new ApiInfo("hexwave.device.setting.service", "", "1.0.0", "", new Contact("", "", ""), "", "",
				new ArrayList<>());
	}
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

}
