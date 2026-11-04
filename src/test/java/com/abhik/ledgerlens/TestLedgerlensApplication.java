package com.abhik.ledgerlens;

import org.springframework.boot.SpringApplication;

public class TestLedgerlensApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerlensApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
