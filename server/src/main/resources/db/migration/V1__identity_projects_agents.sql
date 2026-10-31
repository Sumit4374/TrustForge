CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
 password_hash VARCHAR(255) NOT NULL, verified BOOLEAN NOT NULL DEFAULT FALSE,
 role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ADMIN')), session_generation INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE user_verification_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash CHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX verification_user ON user_verification_tokens(user_id);
CREATE TABLE password_reset_tokens (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash CHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX reset_user ON password_reset_tokens(user_id);
CREATE TABLE projects (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, description VARCHAR(2000) NOT NULL DEFAULT '',
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE project_members (
 project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES users(id),
 role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER','EDITOR','VIEWER')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(project_id,user_id)
);
CREATE INDEX membership_user ON project_members(user_id,project_id);
CREATE TABLE agent_definitions (
 id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 name VARCHAR(120) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX agents_project ON agent_definitions(project_id,created_at);
CREATE TABLE agent_versions (
 id UUID PRIMARY KEY, agent_id UUID NOT NULL REFERENCES agent_definitions(id) ON DELETE CASCADE,
 project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 version INTEGER NOT NULL CHECK (version>0), name VARCHAR(120) NOT NULL,
 instructions VARCHAR(16000) NOT NULL, allowed_tools TEXT NOT NULL, allowed_recipients TEXT NOT NULL,
 allow_send BOOLEAN NOT NULL, max_steps INTEGER NOT NULL CHECK(max_steps BETWEEN 1 AND 12),
 timeout_seconds INTEGER NOT NULL CHECK(timeout_seconds BETWEEN 5 AND 180), model VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(agent_id,version)
);
CREATE INDEX versions_project ON agent_versions(project_id,agent_id);
CREATE TABLE tool_definitions (
 name VARCHAR(64) PRIMARY KEY, description VARCHAR(500) NOT NULL, arguments TEXT NOT NULL,
 version INTEGER NOT NULL DEFAULT 1, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO tool_definitions(name,description,arguments) VALUES
 ('email.read','Read a synthetic inbox email.','["messageId"]'),
 ('email.classify','Classify a synthetic email.','["messageId","classification"]'),
 ('email.send','Send a simulated message; requires permission and scenario approval.','["to","body","approvalId"]'),
 ('document.search','Search synthetic public documents.','["query"]'),
 ('database.lookup','Lookup an allowed synthetic database record.','["recordId"]'),
 ('calendar.create','Create a simulated calendar event.','["calendarId","title"]'),
 ('restricted.read','Read a restricted synthetic record; denied by default.','[]');
CREATE TABLE audit_events (
 id UUID PRIMARY KEY, actor_id UUID, action VARCHAR(100) NOT NULL, target_id UUID,
 outcome VARCHAR(32) NOT NULL, correlation_id VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_actor_time ON audit_events(actor_id,created_at);
