-- CopMap MySQL Database Initialization
CREATE DATABASE IF NOT EXISTS copmap_auth;
GRANT ALL PRIVILEGES ON copmap_auth.* TO 'root'@'%';
FLUSH PRIVILEGES;
