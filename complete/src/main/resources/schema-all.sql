-- this is meant to be created in local postgres

CREATE TABLE company
(
    company_id BIGSERIAL NOT NULL PRIMARY KEY,
    name       VARCHAR(20),
    address    VARCHAR(20)
);

CREATE TABLE people
(
    person_id  BIGSERIAL NOT NULL PRIMARY KEY,
    first_name VARCHAR(20),
    last_name  VARCHAR(20),
    company_id BIGINT REFERENCES company (company_id) DEFERRABLE INITIALLY DEFERRED
);