-- Shared spell-check vocabulary: words every user's spell checker accepts.

CREATE TABLE spellcheck_word (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Case-sensitive: "Datatek" and "DataTek" are different words.
    word           VARCHAR(100) NOT NULL,
    -- 'ALL' for every language, otherwise a language code such as 'nb-NO'.
    language_scope VARCHAR(10)  NOT NULL,
    created_by     VARCHAR(100) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by     VARCHAR(100) NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_spellcheck_word_word_scope UNIQUE (word, language_scope),
    CONSTRAINT ck_spellcheck_word_scope CHECK (language_scope IN ('ALL', 'nb-NO', 'en-US', 'en-GB'))
);

-- Change log. Kept when a word is deleted, so word_id is not a foreign key.
CREATE TABLE spellcheck_word_change (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    word_id                 BIGINT       NOT NULL,
    action                  VARCHAR(10)  NOT NULL,
    word                    VARCHAR(100) NOT NULL,
    language_scope          VARCHAR(10)  NOT NULL,
    -- Values before an update; NULL for additions and deletions.
    previous_word           VARCHAR(100),
    previous_language_scope VARCHAR(10),
    comment                 VARCHAR(500),
    changed_by              VARCHAR(100) NOT NULL,
    changed_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_spellcheck_word_change_action CHECK (action IN ('ADDED', 'UPDATED', 'DELETED'))
);

CREATE INDEX ix_spellcheck_word_change_word_id ON spellcheck_word_change (word_id);
CREATE INDEX ix_spellcheck_word_change_changed_at ON spellcheck_word_change (changed_at DESC);
