package com.example.bank_core;

// [무엇] 테스트용 MySQL 컨테이너 설정
// [역할] 테스트를 실행하면 Docker로 MySQL 8.4를 새로 띄우고, 스프링이 그 DB에 자동으로 연결하게 함 (@ServiceConnection)
//        → 테스트마다 깨끗한 진짜 MySQL에서 돌아가서, 개발용 DB를 건드리지 않음
// [누가 쓰나] 모든 통합 테스트가 @Import(TestcontainersConfiguration.class)로 가져다 씀

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

}
