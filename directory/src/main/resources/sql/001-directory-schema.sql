CREATE SCHEMA directory;

CREATE TABLE directory.structures (
	"id" VARCHAR(36) NOT NULL PRIMARY KEY,
	"external_id" VARCHAR(64) NOT NULL,
	"uai" VARCHAR(8),
	"name" VARCHAR(255),
	"source" VARCHAR(6),
	"created" TIMESTAMPTZ DEFAULT NOW(),
	"modified" TIMESTAMPTZ DEFAULT NOW(),
	"checksum" BIGINT
);

CREATE TABLE directory.classes (
	"id" VARCHAR(36) NOT NULL PRIMARY KEY,
	"external_id" VARCHAR(128) NOT NULL,
	"name" VARCHAR(255),
	"created" TIMESTAMPTZ DEFAULT NOW(),
	"modified" TIMESTAMPTZ DEFAULT NOW(),
	"checksum" BIGINT,
	"structure_id" VARCHAR(36) NOT NULL REFERENCES directory.structures (id) ON DELETE CASCADE
);

CREATE TABLE directory.groups_types (
	"id" SMALLINT NOT NULL PRIMARY KEY,
	"type" VARCHAR(16) NOT NULL
);

INSERT INTO directory.groups_types VALUES (1, 'Profile'), (2, 'Functional'), (3, 'Function'), (4, 'HeadTeacher'), (5, 'Manual'), (6, 'Community'), (7, 'Delete'), (8, 'Remote');

CREATE TABLE directory.groups (
	"id" VARCHAR(36) NOT NULL PRIMARY KEY,
	"external_id" VARCHAR(64),
	"name" VARCHAR(255) NOT NULL,
	"nb_users" int,
	"display_name_search_field" TSVECTOR,
	"filter" VARCHAR(128),
	"communique_user" VARCHAR(1) CHECK ("communique_user" IN ('B','I','O')),
	"communique_relative_student" VARCHAR(1) CHECK ("communique_relative_student" IN ('B','I','O')),
	"communique_with" JSONB,
	"created" TIMESTAMPTZ DEFAULT NOW(),
	"modified" TIMESTAMPTZ DEFAULT NOW(),
	"checksum" BIGINT,
	"structure_id" VARCHAR(36) REFERENCES directory.structures (id) ON DELETE RESTRICT,
	"class_id" VARCHAR(36) REFERENCES directory.classes (id) ON DELETE RESTRICT,
	"parent_group_id" VARCHAR(36) REFERENCES directory.groups (id) ON DELETE RESTRICT,
	"type_id" SMALLINT NOT NULL REFERENCES directory.groups_types (id) ON DELETE RESTRICT
);

CREATE TABLE directory.profiles (
	"id" SMALLINT NOT NULL PRIMARY KEY,
	"profile" VARCHAR(16) NOT NULL
);

INSERT INTO directory.profiles VALUES (1, 'Personnel'), (2, 'Teacher'), (3, 'Student'), (4, 'Relative'), (5, 'Guest'), (6, 'Tech');

CREATE TABLE directory.users (
	"id" VARCHAR(36) NOT NULL PRIMARY KEY,
	"external_id" VARCHAR(64) NOT NULL,
	"display_name" VARCHAR(255) NOT NULL,
	"display_name_search_field" TSVECTOR,
	"blocked" BOOLEAN,
--	"profile" SMALLINT NOT NULL REFERENCES directory.profiles (id) ON DELETE RESTRICT,
	"profile" VARCHAR(16) NOT NULL,
	"created" TIMESTAMPTZ DEFAULT NOW(),
	"modified" TIMESTAMPTZ DEFAULT NOW(),
	"checksum" BIGINT
);

CREATE TABLE directory.groups_users (
	"group_id" VARCHAR(36) NOT NULL REFERENCES directory.groups (id) ON DELETE CASCADE,
	"user_id" VARCHAR(36) NOT NULL REFERENCES directory.users (id) ON DELETE CASCADE,
	PRIMARY KEY (group_id, user_id)
);

CREATE TABLE directory.students_relatives (
	"student_id" VARCHAR(36) NOT NULL REFERENCES directory.users (id) ON DELETE CASCADE,
	"relative_id" VARCHAR(36) NOT NULL REFERENCES directory.users (id) ON DELETE CASCADE,
	PRIMARY KEY (student_id, relative_id)
);

CREATE SCHEMA history;

CREATE TABLE history.events (
	"id" BIGSERIAL PRIMARY KEY,
	"date" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	"module" VARCHAR(32) NOT NULL,
	"entity" VARCHAR(32) NOT NULL,
	"action" VARCHAR(32) NOT NULL,
	"event" JSONB,
	"old" JSONB
);

CREATE OR REPLACE FUNCTION history.log_events() RETURNS TRIGGER AS $body$
BEGIN
    IF (TG_OP = 'UPDATE') THEN
        INSERT INTO history.events (module,entity,action,event,old)
        VALUES (TG_TABLE_SCHEMA,TG_TABLE_NAME,substring(TG_OP,1,1),to_jsonb(NEW),to_jsonb(OLD));
        RETURN NEW;
    ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO history.events (module,entity,action,event,old)
        VALUES (TG_TABLE_SCHEMA,TG_TABLE_NAME,substring(TG_OP,1,1),NULL,to_jsonb(OLD));
        RETURN OLD;
    ELSE
        RAISE WARNING '[HISTORY.LOG_EVENTS] - Other action occurred: %, at %',TG_OP,now();
        RETURN NULL;
    END IF;

EXCEPTION
    WHEN data_exception THEN
        RAISE WARNING '[HISTORY.LOG_EVENTS] - UDF ERROR [DATA EXCEPTION] - SQLSTATE: %, SQLERRM: %',SQLSTATE,SQLERRM;
        RETURN NULL;
    WHEN unique_violation THEN
        RAISE WARNING '[HISTORY.LOG_EVENTS] - UDF ERROR [UNIQUE] - SQLSTATE: %, SQLERRM: %',SQLSTATE,SQLERRM;
        RETURN NULL;
    WHEN OTHERS THEN
        RAISE WARNING '[HISTORY.LOG_EVENTS] - UDF ERROR [OTHER] - SQLSTATE: %, SQLERRM: %',SQLSTATE,SQLERRM;
        RETURN NULL;
END;
$body$
LANGUAGE 'plpgsql';

CREATE TRIGGER directory_groups_events_trg AFTER UPDATE OR DELETE ON directory.groups FOR EACH ROW EXECUTE PROCEDURE history.log_events();

CREATE TRIGGER directory_users_events_trg AFTER UPDATE OR DELETE ON directory.users FOR EACH ROW EXECUTE PROCEDURE history.log_events();

CREATE TABLE directory.scripts (
	"filename" VARCHAR(255) NOT NULL PRIMARY KEY,
	"passed" TIMESTAMP NOT NULL DEFAULT NOW()
);
