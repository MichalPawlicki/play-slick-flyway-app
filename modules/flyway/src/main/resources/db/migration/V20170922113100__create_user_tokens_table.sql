create table "user_tokens" (
  "id" UUID PRIMARY KEY NOT NULL,
  user_id VARCHAR(255) NOT NULL REFERENCES "users" (id),
  "email" VARCHAR(1024) NOT NULL,
  expiration_time TIMESTAMP NOT NULL,
  is_sign_up BOOLEAN NOT NULL
);
