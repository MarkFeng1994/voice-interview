package com.interview.module.interview.engine.model;

public class RealtimeMetrics {

	private int totalTurns;
	private int interruptCount;
	private long avgResponseLatencyMs;
	private long effectiveDurationMs;
	private long realtimeStartedAt;
	private long realtimeEndedAt;

	public int getTotalTurns() {
		return totalTurns;
	}

	public void setTotalTurns(int totalTurns) {
		this.totalTurns = totalTurns;
	}

	public int getInterruptCount() {
		return interruptCount;
	}

	public void setInterruptCount(int interruptCount) {
		this.interruptCount = interruptCount;
	}

	public long getAvgResponseLatencyMs() {
		return avgResponseLatencyMs;
	}

	public void setAvgResponseLatencyMs(long avgResponseLatencyMs) {
		this.avgResponseLatencyMs = avgResponseLatencyMs;
	}

	public long getEffectiveDurationMs() {
		return effectiveDurationMs;
	}

	public void setEffectiveDurationMs(long effectiveDurationMs) {
		this.effectiveDurationMs = effectiveDurationMs;
	}

	public long getRealtimeStartedAt() {
		return realtimeStartedAt;
	}

	public void setRealtimeStartedAt(long realtimeStartedAt) {
		this.realtimeStartedAt = realtimeStartedAt;
	}

	public long getRealtimeEndedAt() {
		return realtimeEndedAt;
	}

	public void setRealtimeEndedAt(long realtimeEndedAt) {
		this.realtimeEndedAt = realtimeEndedAt;
	}

	public void incrementTurns() {
		this.totalTurns++;
	}

	public void incrementInterrupts() {
		this.interruptCount++;
	}
}
