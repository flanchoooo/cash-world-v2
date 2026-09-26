ALTER TABLE users
  ADD COLUMN permissions_customized BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_permissions (
  user_id CHAR(36) NOT NULL,
  permission_code VARCHAR(50) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, permission_code),
  CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
