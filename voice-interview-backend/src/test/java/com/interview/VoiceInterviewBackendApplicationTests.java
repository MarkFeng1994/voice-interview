package com.interview;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.profiles.active=dev,test")
@Disabled("Requires MySQL and full Spring context — run manually or in CI")
class VoiceInterviewBackendApplicationTests {

	@Test
	void contextLoads() {
	}
}
