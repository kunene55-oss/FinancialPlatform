package com.example.financial.processing;

import com.example.financial.processing.config.LocalSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"local", "dev", "qa"})
@Import(LocalSecurityConfig.class)
class ProcessingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
