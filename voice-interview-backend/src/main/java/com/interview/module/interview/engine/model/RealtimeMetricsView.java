package com.interview.module.interview.engine.model;

public record RealtimeMetricsView(
		int totalTurns,
		int interruptCount,
		long avgResponseLatencyMs,
		long effectiveDurationMs
) {
}
