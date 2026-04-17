package com.interview.module.interview.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.module.interview.entity.ReportEntity;
import com.interview.module.interview.entity.SessionEntity;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.mapper.ReportMapper;
import com.interview.module.interview.mapper.SessionMapper;

class JdbcInterviewReportStoreTest {

	@Test
	void should_find_persisted_report_with_report_version() throws Exception {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, objectMapper);
		InterviewReportView report = legacyReport("session-1");

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);

		ReportEntity reportEntity = new ReportEntity();
		reportEntity.setSessionId(11L);
		reportEntity.setReportJson(objectMapper.writeValueAsString(report));
		reportEntity.setReportVersion("v1");
		when(reportMapper.selectOne(any())).thenReturn(reportEntity);

		PersistedInterviewReport persisted = store.findPersistedReportBySessionId("session-1").orElseThrow();

		assertThat(persisted.report()).isEqualTo(report);
		assertThat(persisted.reportVersion()).isEqualTo("v1");
	}

	@Test
	void should_map_blank_persisted_report_version_to_null() throws Exception {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		ObjectMapper objectMapper = new ObjectMapper();
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, objectMapper);
		InterviewReportView report = legacyReport("session-1");

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);

		ReportEntity reportEntity = new ReportEntity();
		reportEntity.setSessionId(11L);
		reportEntity.setReportJson(objectMapper.writeValueAsString(report));
		reportEntity.setReportVersion("   ");
		when(reportMapper.selectOne(any())).thenReturn(reportEntity);

		PersistedInterviewReport persisted = store.findPersistedReportBySessionId("session-1").orElseThrow();

		assertThat(persisted.reportVersion()).isNull();
	}

	@Test
	void should_persist_explicit_report_version_on_insert_save() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, new ObjectMapper());

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(reportMapper.selectOne(any())).thenReturn(null);

		store.save(legacyReport("session-1"), "v2");

		ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).insert(captor.capture());
		assertThat(captor.getValue().getReportVersion()).isEqualTo("v2");
	}

	@Test
	void should_persist_latest_version_when_save_without_explicit_version() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		InterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, new ObjectMapper());

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(reportMapper.selectOne(any())).thenReturn(null);

		store.save(legacyReport("session-1"));

		ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).insert(captor.capture());
		assertThat(captor.getValue().getReportVersion()).isEqualTo(InterviewReportStore.LATEST_REPORT_VERSION);
	}

	@Test
	void should_normalize_null_report_version_to_latest_on_save() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, new ObjectMapper());

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(reportMapper.selectOne(any())).thenReturn(null);

		store.save(legacyReport("session-1"), null);

		ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).insert(captor.capture());
		assertThat(captor.getValue().getReportVersion()).isEqualTo(InterviewReportStore.LATEST_REPORT_VERSION);
	}

	@Test
	void should_normalize_blank_report_version_to_latest_on_save() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, new ObjectMapper());

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);
		when(reportMapper.selectOne(any())).thenReturn(null);

		store.save(legacyReport("session-1"), "   ");

		ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).insert(captor.capture());
		assertThat(captor.getValue().getReportVersion()).isEqualTo(InterviewReportStore.LATEST_REPORT_VERSION);
	}

	@Test
	void should_persist_report_version_on_update_branch() {
		SessionMapper sessionMapper = mock(SessionMapper.class);
		ReportMapper reportMapper = mock(ReportMapper.class);
		JdbcInterviewReportStore store = new JdbcInterviewReportStore(sessionMapper, reportMapper, new ObjectMapper());

		SessionEntity sessionEntity = new SessionEntity();
		sessionEntity.setId(11L);
		sessionEntity.setSessionKey("session-1");
		when(sessionMapper.selectOne(any())).thenReturn(sessionEntity);

		ReportEntity existing = new ReportEntity();
		existing.setId(99L);
		existing.setSessionId(11L);
		existing.setReportVersion("v1");
		when(reportMapper.selectOne(any())).thenReturn(existing);

		store.save(legacyReport("session-1"), "v3");

		ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
		verify(reportMapper).updateById(captor.capture());
		assertThat(captor.getValue().getId()).isEqualTo(99L);
		assertThat(captor.getValue().getReportVersion()).isEqualTo("v3");
	}

	private static InterviewReportView legacyReport(String sessionId) {
		return new InterviewReportView(
				sessionId,
				"COMPLETED",
				"Legacy Report",
				80,
				"Solid answer quality",
				List.of("Strength"),
				List.of("Weakness"),
				List.of("Suggestion"),
				List.of(),
				null
		, null);
	}
}
