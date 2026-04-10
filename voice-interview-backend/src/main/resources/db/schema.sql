-- Voice Interview schema bootstrap
-- This file is intentionally not auto-applied yet.
-- It serves as the first persistence-ready baseline for the current in-memory/redis prototype.

CREATE TABLE IF NOT EXISTS t_user (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    password     VARCHAR(128) NOT NULL,
    nickname     VARCHAR(64)  NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_category (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    name         VARCHAR(64) NOT NULL,
    parent_id    BIGINT      NOT NULL DEFAULT 0,
    sort_order   INT         NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    INDEX idx_user_parent (user_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_question (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    category_id   BIGINT        NOT NULL,
    title         VARCHAR(512)  NOT NULL,
    content       TEXT          NOT NULL,
    answer        TEXT,
    difficulty    TINYINT       NOT NULL DEFAULT 1,
    source        VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    source_url    VARCHAR(512),
    tags_json     JSON,
    used_count    INT           NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    INDEX idx_user_category (user_id, category_id),
    INDEX idx_user_difficulty (user_id, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_interview_session (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_key             VARCHAR(64)  NOT NULL UNIQUE,
    user_id                 BIGINT       NOT NULL,
    title                   VARCHAR(128),
    selected_category_ids   JSON         NOT NULL,
    config_json             JSON         NOT NULL,
    interaction_mode        VARCHAR(16)  NOT NULL DEFAULT 'PUSH_TO_TALK',
    status                  VARCHAR(16)  NOT NULL DEFAULT 'CREATED',
    current_question_index  INT          NOT NULL DEFAULT 0,
    total_question_count    INT          NOT NULL DEFAULT 5,
    overall_score           INT,
    overall_comment         TEXT,
    started_at              DATETIME,
    finished_at             DATETIME,
    last_active_at          DATETIME,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_interview_question (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id           BIGINT        NOT NULL,
    question_id          BIGINT        NOT NULL,
    question_index       INT           NOT NULL,
    category_id          BIGINT        NOT NULL DEFAULT 0,
    title_snapshot       VARCHAR(512)  NOT NULL,
    content_snapshot     TEXT          NOT NULL,
    answer_snapshot      TEXT,
    difficulty_snapshot  TINYINT       NOT NULL DEFAULT 1,
    source_snapshot      VARCHAR(32)   NOT NULL DEFAULT 'MANUAL',
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_question_idx (session_id, question_index),
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_media_file (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT,
    biz_type      VARCHAR(32)  NOT NULL,
    storage_type  VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    file_key      VARCHAR(512) NOT NULL,
    mime_type     VARCHAR(128) NOT NULL,
    duration_ms   BIGINT       NOT NULL DEFAULT 0,
    size_bytes    BIGINT       NOT NULL DEFAULT 0,
    expire_at     DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_biz (user_id, biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_interview_round (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id             BIGINT         NOT NULL,
    interview_question_id  BIGINT         NOT NULL,
    question_index         INT            NOT NULL,
    follow_up_index        INT            NOT NULL DEFAULT 0,
    round_type             VARCHAR(16)    NOT NULL,
    user_audio_file_id     BIGINT,
    user_answer_mode       VARCHAR(16)    NOT NULL DEFAULT 'VOICE',
    asr_text               MEDIUMTEXT,
    final_user_answer_text MEDIUMTEXT,
    ai_message_text        MEDIUMTEXT     NOT NULL,
    ai_analysis            MEDIUMTEXT,
    tts_audio_file_id      BIGINT,
    tts_audio_url          VARCHAR(512),
    score                  INT,
    duration_ms            BIGINT         NOT NULL DEFAULT 0,
    llm_trace_id           VARCHAR(128),
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at            DATETIME,
    INDEX idx_session_question (session_id, question_index),
    INDEX idx_interview_question_id (interview_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_interview_report (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id      BIGINT       NOT NULL UNIQUE,
    overall_score   INT,
    overall_comment TEXT,
    report_json     JSON         NOT NULL,
    report_version  VARCHAR(32)  NOT NULL DEFAULT 'v1',
    generated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_import_task (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    category_id   BIGINT       NOT NULL,
    file_name     VARCHAR(256),
    source_url    VARCHAR(512),
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    total_count   INT          NOT NULL DEFAULT 0,
    success_count INT          NOT NULL DEFAULT 0,
    error_msg     TEXT,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_resume_profile (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    media_file_id      VARCHAR(128) NOT NULL,
    original_file_name VARCHAR(256) NOT NULL,
    content_type       VARCHAR(128) NOT NULL,
    size_bytes         BIGINT       NOT NULL DEFAULT 0,
    parse_status       VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',
    resume_summary     TEXT,
    extracted_keywords JSON,
    project_highlights JSON,
    parse_error        TEXT,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_media_file (user_id, media_file_id),
    INDEX idx_user_parse_status (user_id, parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
