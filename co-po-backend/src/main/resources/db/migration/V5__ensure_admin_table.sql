-- Safety migration for deployments where the Admin table is missing even though
-- the rest of the schema exists. This is intentionally idempotent so it can run on
-- both fresh installs and partially migrated/adopted databases.

CREATE TABLE IF NOT EXISTS Admin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    is_super_admin BOOLEAN DEFAULT FALSE,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO Admin (email, password, is_super_admin, created_by)
VALUES ('admin@iut-dhaka.edu', 'Password123', TRUE, 'system');
