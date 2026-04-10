package com.interview.module.system.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.interview.common.result.ApiResponse;

@RestController
public class HealthController {

	@GetMapping("/health")
	public ApiResponse<HealthPayload> health() {
		return ApiResponse.success(new HealthPayload(
				"UP",
				"voice-interview-backend",
				Instant.now().toString()
		));
	}

	public record HealthPayload(
			String status,
			String service,
			String timestamp
	) {
	}
}
