package com.interview.module.interview.engine.store;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.entity.ReportEntity;
import com.interview.module.interview.entity.SessionEntity;
import com.interview.module.interview.mapper.ReportMapper;
import com.interview.module.interview.mapper.SessionMapper;
import com.interview.module.interview.engine.model.InterviewReportView;

@Component
@ConditionalOnProperty(prefix = "app.interview", name = "session-store", havingValue = "jdbc")
public class JdbcInterviewReportStore implements InterviewReportStore {

	private final SessionMapper sessionMapper;
	private final ReportMapper reportMapper;
	private final ObjectMapper objectMapper;

	public JdbcInterviewReportStore(SessionMapper sessionMapper, ReportMapper reportMapper, ObjectMapper objectMapper) {
		this.sessionMapper = sessionMapper;
		this.reportMapper = reportMapper;
		this.objectMapper = objectMapper;
	}

	@Override
	public Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId) {
		Optional<Long> internalId = findInternalSessionId(sessionId);
		if (internalId.isEmpty()) return Optional.empty();

		ReportEntity entity = reportMapper.selectOne(
				new LambdaQueryWrapper<ReportEntity>().eq(ReportEntity::getSessionId, internalId.get()));
		if (entity == null) return Optional.empty();
		return Optional.of(new PersistedInterviewReport(
				deserializeReport(entity.getReportJson()),
				entity.getReportVersion()));
	}

	@Override
	public void save(InterviewReportView report, String reportVersion) {
		long internalSessionId = findInternalSessionId(report.sessionId())
				.orElseThrow(() -> new IllegalArgumentException("Interview session not found: " + report.sessionId()));
		String reportJson = serializeReport(report);
		String normalizedReportVersion = normalizeReportVersion(reportVersion);

		ReportEntity existing = reportMapper.selectOne(
				new LambdaQueryWrapper<ReportEntity>().eq(ReportEntity::getSessionId, internalSessionId));

		if (existing == null) {
			ReportEntity entity = new ReportEntity();
			entity.setSessionId(internalSessionId);
			entity.setOverallScore(report.overallScore());
			entity.setOverallComment(report.overallComment());
			entity.setReportJson(reportJson);
			entity.setReportVersion(normalizedReportVersion);
			reportMapper.insert(entity);
		} else {
			existing.setOverallScore(report.overallScore());
			existing.setOverallComment(report.overallComment());
			existing.setReportJson(reportJson);
			existing.setReportVersion(normalizedReportVersion);
			reportMapper.updateById(existing);
		}
	}

	private Optional<Long> findInternalSessionId(String sessionKey) {
		SessionEntity entity = sessionMapper.selectOne(
				new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getSessionKey, sessionKey));
		return entity == null ? Optional.empty() : Optional.of(entity.getId());
	}

	private String serializeReport(InterviewReportView report) {
		try {
			return objectMapper.writeValueAsString(report);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize interview report", ex);
		}
	}

	private InterviewReportView deserializeReport(String value) {
		try {
			return objectMapper.readValue(value, InterviewReportView.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize interview report", ex);
		}
	}

	private String normalizeReportVersion(String reportVersion) {
		if (reportVersion == null || reportVersion.isBlank()) {
			return InterviewReportStore.LATEST_REPORT_VERSION;
		}
		return reportVersion;
	}
}
