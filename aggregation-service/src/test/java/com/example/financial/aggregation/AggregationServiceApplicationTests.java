package com.example.financial.aggregation;

import com.example.financial.aggregation.config.LocalSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"local", "dev", "qa"})
@Import(LocalSecurityConfig.class)
class AggregationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
