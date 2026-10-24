user demo;
insert ignore into users 
    (
        username, 
        password_hash, 
        nickname
    ) 
values (
    'testuser1', 
    '$2a$10$aePkItP2ehCRYiZyQLPt9eHGTK9g8UDI8QOAq1PElaq.J8SGBea/K', --1234
    'TestUser1'
),
(
    'testuser2', 
    '$2a$10$aePkItP2ehCRYiZyQLPt9eHGTK9g8UDI8QOAq1PElaq.J8SGBea/K', --1234
    'TestUser2'
);