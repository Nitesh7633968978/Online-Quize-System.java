# Online Quiz System (Java + Swing + MySQL)

A minimal but complete starter you can run, extend, and screenshot. It includes:

* MySQL schema + sample data
* Java Swing app (login → timed quiz → results with correct answers)
* Randomized questions per user attempt
* Score tracking and DB persistence
* CSV report export

---

## 1) MySQL Schema & Sample Data (`schema.sql`)

```sql
-- Create database
CREATE DATABASE IF NOT EXISTS quiz_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE quiz_app;

-- Users
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Quizzes
CREATE TABLE IF NOT EXISTS quizzes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  total_questions INT NOT NULL,
  time_limit_seconds INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
);

-- Questions
CREATE TABLE IF NOT EXISTS questions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  quiz_id INT NOT NULL,
  question_text TEXT NOT NULL,
  option_a VARCHAR(255) NOT NULL,
  option_b VARCHAR(255) NOT NULL,
  option_c VARCHAR(255) NOT NULL,
  option_d VARCHAR(255) NOT NULL,
  correct_option CHAR(1) NOT NULL CHECK (correct_option IN ('A','B','C','D')),
  points INT NOT NULL DEFAULT 1,
  FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

-- Results (attempts)
CREATE TABLE IF NOT EXISTS results (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  quiz_id INT NOT NULL,
  score INT NOT NULL,
  total INT NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NOT NULL,
  duration_seconds INT NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

-- Result answers (per-question detail)
CREATE TABLE IF NOT EXISTS result_answers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  result_id INT NOT NULL,
  question_id INT NOT NULL,
  chosen_option CHAR(1) NOT NULL,
  is_correct TINYINT(1) NOT NULL,
  FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE CASCADE,
  FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- Sample data ---------------------------------------------------------------
-- Demo user: username: demo / password: demo123
-- We'll store SHA-256 hash of the password (computed in Java insert below).

INSERT INTO quizzes (title, total_questions, time_limit_seconds, active)
VALUES
  ("Java Basics", 5, 90, 1)
ON DUPLICATE KEY UPDATE title=VALUES(title);

-- Get the quiz id (assume it's 1 for this seed). If not, adjust IDs.
SET @quiz_id := (SELECT id FROM quizzes WHERE title='Java Basics' LIMIT 1);

INSERT INTO questions (quiz_id, question_text, option_a, option_b, option_c, option_d, correct_option, points) VALUES
(@quiz_id, 'Which keyword is used to inherit a class in Java?', 'extend', 'extends', 'inherit', 'implements', 'B', 1),
(@quiz_id, 'Which method is the entry point for a Java app?', 'start()', 'main()', 'run()', 'init()', 'B', 1),
(@quiz_id, 'Which collection does not allow duplicates?', 'List', 'Set', 'Map', 'Queue', 'B', 1),
(@quiz_id, 'What is JVM?', 'Java Virtual Machine', 'Java Vendor Model', 'Just Virtual Maker', 'Java Variable Memory', 'A', 1),
(@quiz_id, 'Which access modifier is most restrictive?', 'private', 'protected', 'public', 'default', 'A', 1),
(@quiz_id, 'Which keyword prevents method overriding?', 'final', 'static', 'const', 'abstract', 'A', 1),
(@quiz_id, 'Which package contains ArrayList?', 'java.util', 'java.lang', 'java.io', 'java.net', 'A', 1);

-- Create demo user (inserted from app if missing).
```

> After running the app once, it will auto-create the demo user (demo / demo123) if not found.

---

## 2) Project Structure

```
quiz-app-swing/
├─ src/
│  ├─ app/Main.java
│  ├─ app/util/DB.java
│  ├─ app/util/Security.java
│  ├─ app/model/User.java
│  ├─ app/model/Quiz.java
│  ├─ app/model/Question.java
│  ├─ app/model/Result.java
│  ├─ app/model/ResultAnswer.java
│  ├─ app/dao/UserDAO.java
│  ├─ app/dao/QuizDAO.java
│  ├─ app/dao/QuestionDAO.java
│  ├─ app/dao/ResultDAO.java
│  ├─ app/ui/LoginFrame.java
│  ├─ app/ui/QuizFrame.java
│  └─ app/ui/ResultFrame.java
├─ resources/
│  └─ db.properties
└─ schema.sql
```

---

## 3) DB Properties (`resources/db.properties`)

```properties
jdbc.url=jdbc:mysql://localhost:3306/quiz_app?useSSL=false&serverTimezone=UTC
jdbc.user=root
jdbc.password=YOUR_PASSWORD
```

---

## 4) Java Code

> Paste these files under `src/` matching the package folders shown above.

### `app/util/DB.java`

```java
package app.util;

import java.sql.*;
import java.util.Properties;
import java.io.InputStream;

public class DB {
    private static Connection conn;

    public static Connection getConnection() throws Exception {
        if (conn != null && !conn.isClosed()) return conn;
        Properties props = new Properties();
        try (InputStream in = DB.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) throw new RuntimeException("db.properties not found in classpath");
            props.load(in);
        }
        String url = props.getProperty("jdbc.url");
        String user = props.getProperty("jdbc.user");
        String pass = props.getProperty("jdbc.password");
        conn = DriverManager.getConnection(url, user, pass);
        return conn;
    }
}
```

### `app/util/Security.java`

```java
package app.util;

import java.security.MessageDigest;

public class Security {
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### `app/model/User.java`

```java
package app.model;

public class User {
    private int id;
    private String username;
    private String fullName;

    public User(int id, String username, String fullName) {
        this.id = id; this.username = username; this.fullName = fullName;
    }
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
}
```

### `app/model/Quiz.java`

```java
package app.model;

public class Quiz {
    private int id;
    private String title;
    private int totalQuestions;
    private int timeLimitSeconds;

    public Quiz(int id, String title, int totalQuestions, int timeLimitSeconds) {
        this.id = id; this.title = title; this.totalQuestions = totalQuestions; this.timeLimitSeconds = timeLimitSeconds;
    }
    public int getId() { return id; }
    public String getTitle() { return title; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
}
```

### `app/model/Question.java`

```java
package app.model;

public class Question {
    private int id;
    private int quizId;
    private String text;
    private String a,b,c,d;
    private char correct;
    private int points;

    public Question(int id, int quizId, String text, String a, String b, String c, String d, char correct, int points)
```
