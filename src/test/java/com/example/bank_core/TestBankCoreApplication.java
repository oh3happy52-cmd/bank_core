package com.example.bank_core;

import org.springframework.boot.SpringApplication;

public class TestBankCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(BankCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
