CREATE SCHEMA communication;

CREATE TABLE communication.communications (
	"id" BIGSERIAL PRIMARY KEY,
	"user_id" VARCHAR(36) NOT NULL REFERENCES directory.users (id) ON DELETE CASCADE,
	"dest_group_id" VARCHAR(36),
--	 REFERENCES directory.groups (id) ON DELETE CASCADE,
    "dest_user_id" VARCHAR(36) REFERENCES directory.users (id) ON DELETE CASCADE,
    "distance" SMALLINT NOT NULL,
    UNIQUE (user_id, dest_group_id),
    UNIQUE (user_id, dest_user_id)
);

CREATE TABLE communication.scripts (
	"filename" VARCHAR(255) NOT NULL PRIMARY KEY,
	"passed" TIMESTAMP NOT NULL DEFAULT NOW()
);
