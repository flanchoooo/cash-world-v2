CREATE TABLE expense_categories (
  id CHAR(36) PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  name VARCHAR(100) NOT NULL UNIQUE,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE expense_vendors (
  id CHAR(36) PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  name VARCHAR(200) NOT NULL UNIQUE,
  contact_name VARCHAR(150),
  mobile_number VARCHAR(40),
  email VARCHAR(254),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE expense_employees (
  id CHAR(36) PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  name VARCHAR(150) NOT NULL,
  employee_number VARCHAR(50) UNIQUE,
  mobile_number VARCHAR(40),
  email VARCHAR(254),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uq_expense_employee_name (name)
) ENGINE=InnoDB;

ALTER TABLE business_expenses
  ADD COLUMN category_id CHAR(36),
  ADD COLUMN requested_by_employee_id CHAR(36),
  ADD COLUMN vendor_id CHAR(36),
  ADD CONSTRAINT fk_business_expenses_category FOREIGN KEY (category_id) REFERENCES expense_categories(id),
  ADD CONSTRAINT fk_business_expenses_employee FOREIGN KEY (requested_by_employee_id) REFERENCES expense_employees(id),
  ADD CONSTRAINT fk_business_expenses_vendor FOREIGN KEY (vendor_id) REFERENCES expense_vendors(id);

CREATE TABLE expense_attachments (
  id CHAR(36) PRIMARY KEY,
  expense_id CHAR(36) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  file_size BIGINT NOT NULL,
  data LONGBLOB NOT NULL,
  uploaded_by_user_id CHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_expense_attachments_expense FOREIGN KEY (expense_id) REFERENCES business_expenses(id) ON DELETE CASCADE,
  CONSTRAINT fk_expense_attachments_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id),
  INDEX idx_expense_attachments_expense (expense_id)
) ENGINE=InnoDB;

INSERT INTO expense_categories(id,created_at,updated_at,name,status) VALUES
  (UUID(),NOW(6),NOW(6),'Travel','ACTIVE'),
  (UUID(),NOW(6),NOW(6),'Office supplies','ACTIVE'),
  (UUID(),NOW(6),NOW(6),'Utilities','ACTIVE'),
  (UUID(),NOW(6),NOW(6),'Rent','ACTIVE'),
  (UUID(),NOW(6),NOW(6),'Staff welfare','ACTIVE'),
  (UUID(),NOW(6),NOW(6),'Other','ACTIVE')
ON DUPLICATE KEY UPDATE
  status = IF(status IS NULL, VALUES(status), status);
