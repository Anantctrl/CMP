CREATE DATABASE IF NOT EXISTS mydatabase;
-- Do not commit real passwords - set this value locally if you use this user.
CREATE USER IF NOT EXISTS 'myuser'@'%' IDENTIFIED BY 'CHANGE_ME_LOCALLY';
GRANT ALL PRIVILEGES ON mydatabase.* TO 'myuser'@'%';
