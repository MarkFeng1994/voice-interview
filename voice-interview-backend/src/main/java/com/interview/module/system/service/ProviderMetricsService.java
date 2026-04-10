package com.interview.module.system.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class ProviderMetricsService {

	private final Map<String, MutableMetric> metrics = new ConcurrentHashMap<>();

	public <T> T record(String capability, String provider, ProviderCall<T> call) {
		long startedAt = System.nanoTime();
		try {
			T result = call.run();
			recordSuccess(capability, provider, startedAt);
			return result;
		} catch (RuntimeException ex) {
			recordFailure(capability, provider, startedAt, ex.getMessage());
			throw ex;
		} catch (Exception ex) {
			recordFailure(capability, provider, startedAt, ex.getMessage());
			throw new IllegalStateException(ex);
		}
	}

	public List<ProviderMetricView> snapshot() {
		return metrics.values().stream()
				.map(MutableMetric::toView)
				.sorted(Comparator.comparing(ProviderMetricView::capability).thenComparing(ProviderMetricView::provider))
				.toList();
	}

	private void recordSuccess(String capability, String provider, long startedAt) {
		MutableMetric metric = metrics.computeIfAbsent(metricKey(capability, provider), key -> new MutableMetric(capability, provider));
		metric.recordSuccess(elapsedMs(startedAt));
	}

	private void recordFailure(String capability, String provider, long startedAt, String message) {
		MutableMetric metric = metrics.computeIfAbsent(metricKey(capability, provider), key -> new MutableMetric(capability, provider));
		metric.recordFailure(elapsedMs(startedAt), message);
	}

	private long elapsedMs(long startedAt) {
		return Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
	}

	private String metricKey(String capability, String provider) {
		return capability + ":" + provider;
	}

	@FunctionalInterface
	public interface ProviderCall<T> {
		T run() throws Exception;
	}

	public record ProviderMetricView(
			String capability,
			String provider,
			long totalCalls,
			long successCalls,
			long failureCalls,
			long averageLatencyMs,
			long lastLatencyMs,
			LocalDateTime lastCalledAt,
			LocalDateTime lastSuccessAt,
			LocalDateTime lastFailureAt,
			String lastError
	) {
	}

	private static final class MutableMetric {

		private final String capability;
		private final String provider;
		private long totalCalls;
		private long successCalls;
		private long failureCalls;
		private long totalLatencyMs;
		private long lastLatencyMs;
		private LocalDateTime lastCalledAt;
		private LocalDateTime lastSuccessAt;
		private LocalDateTime lastFailureAt;
		private String lastError;

		private MutableMetric(String capability, String provider) {
			this.capability = capability;
			this.provider = provider;
		}

		private synchronized void recordSuccess(long latencyMs) {
			totalCalls++;
			successCalls++;
			totalLatencyMs += latencyMs;
			lastLatencyMs = latencyMs;
			lastCalledAt = LocalDateTime.now();
			lastSuccessAt = lastCalledAt;
		}

		private synchronized void recordFailure(long latencyMs, String message) {
			totalCalls++;
			failureCalls++;
			totalLatencyMs += latencyMs;
			lastLatencyMs = latencyMs;
			lastCalledAt = LocalDateTime.now();
			lastFailureAt = lastCalledAt;
			lastError = message;
		}

		private synchronized ProviderMetricView toView() {
			long averageLatencyMs = totalCalls == 0 ? 0L : Math.round((double) totalLatencyMs / totalCalls);
			return new ProviderMetricView(
					capability,
					provider,
					totalCalls,
					successCalls,
					failureCalls,
					averageLatencyMs,
					lastLatencyMs,
					lastCalledAt,
					lastSuccessAt,
					lastFailureAt,
					lastError
			);
		}
	}
}
