# KPOK2 - Unique Test Questions Collection System

## Project Description
KPOK2 is a Java application based on Spring Boot, designed for automated collection and storage of unique test questions from the TestCenter platform for "KROK 2" exam preparation. The system automates the process of logging in, taking tests, and collecting results, storing unique questions in a database for future use.

## Tech Stack
- **Java 17**
- **Spring Boot** - main framework
- **Spring Data JPA** - for database operations
- **Selenium WebDriver** - for browser automation
- **Lombok** - for reducing boilerplate code
- **MapStruct** - for mapping between DTOs and entities
- **H2/PostgreSQL** - database (specify the current one)

## Project Structure

### Main Packages
- `com.myprojects.kpok2` - root package
  - `.config` - Spring, Selenium, async operations configurations
  - `.controller` - REST API controllers
  - `.exception` - exception handling
  - `.model` - entities and DTOs
  - `.repository` - JPA repositories
  - `.service` - business logic
    - `.parser` - test parsing logic
    - `.mapper` - conversion between DTOs and entities
  - `.util` - utility classes and constants

### Key Components

#### Data Models
- `TestQuestion` - main entity representing a test question with answers
- `ParsedTestQuestionDto` - DTO for transferring question data between components
- `TestParsingResultDto` - DTO for parsing results

#### Services
- `TestQuestionService` - managing question storage and retrieval
- `AuthenticationService` - authentication on the TestCenter platform
- `TestParser` - coordinating the parsing process
- `TestPageParser` - parsing HTML pages using Selenium
- `TestParsingExecutor` - asynchronous execution of parsing tasks

#### Configurations
- `SeleniumConfig` - WebDriver configuration for Selenium
- `AsyncConfig` - thread pool configuration for asynchronous operations
- `TestCenterConfig` - TestCenter access configuration

#### API
- `ParserController` - REST API for initiating parsing and retrieving results

## System Workflow

1. **Authentication**
   - The system logs into TestCenter using credentials from the configuration

2. **Test Parsing**
   - The system opens the test URL via Selenium
   - Parses questions, answer options, and correct answers
   - Normalizes question text to determine uniqueness

3. **Data Storage**
   - An MD5 hash of the normalized text is generated for each question
   - Question uniqueness is checked by hash
   - Unique questions are stored in the database

4. **Question Search and Retrieval**
   - API allows searching questions by text fragments
   - Retrieval of all saved questions is possible

## Implementation Features

### Question Uniqueness
The system determines question uniqueness based on the MD5 hash of the normalized question text. Normalization includes:
- Removing extra spaces
- Converting to lowercase
- Removing HTML tags (if any)

### Asynchronous Processing
The system uses a thread pool for parallel parsing of multiple tests. The configuration in `AsyncConfig` allows adjusting the number of concurrent operations.

### Error Handling
A retry mechanism is implemented for failed parsing attempts. The system tracks the status of each parsing operation and can retry failed operations.

## Setup and Launch

### Prerequisites
- JDK 17 or higher
- Maven
- ChromeDriver (for Selenium)

### Configuration
Main settings are in `application.properties` or `application.yml`:
```properties
# TestCenter Access
testcenter.username=your_username
testcenter.password=your_password

# Database Configuration
spring.datasource.url=jdbc:h2:file:./kpok2db
spring.datasource.username=sa
spring.datasource.password=password

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
```

### Running the Application
```bash
mvn spring-boot:run
```

## API Endpoints

- `POST /api/parse?url={testUrl}` - Start parsing a test at the specified URL
- `GET /api/parse/results` - Get the results of the last parsing
- `GET /api/questions` - Get all saved questions
- `GET /api/questions/search?query={searchText}` - Search questions by text

## Development Plans
- Improving the question uniqueness algorithm
- Adding support for images in questions
- Expanding search capabilities
- Optimizing parsing performance

## Local Development Configuration

### Setting up application-local.properties

To run the application locally, you need to create an `application-local.properties` file in the `src/main/resources` directory. This file is excluded from Git to keep sensitive information secure.

Use the following template as a reference:

```properties
# Local Development Configuration
# This file is excluded from Git and should contain your local environment settings

# Database Configuration (if needed)
# spring.datasource.url=jdbc:postgresql://localhost:5432/kpok2_db
# spring.datasource.username=your_username
# spring.datasource.password=your_password

# Logging Configuration
logging.level.com.myprojects.kpok2=DEBUG

# TestCenter Account Configuration
# Format: testcenter.accounts[index].username=username
#         testcenter.accounts[index].password=password
#         testcenter.accounts[index].enabled=true/false

# Account 1
testcenter.accounts[0].username=your_username1
testcenter.accounts[0].password=your_password1
testcenter.accounts[0].enabled=true

# Account 2
testcenter.accounts[1].username=your_username2
testcenter.accounts[1].password=your_password2
testcenter.accounts[1].enabled=true

# You can add more accounts as needed
# testcenter.accounts[2].username=your_username3
# testcenter.accounts[2].password=your_password3
# testcenter.accounts[2].enabled=false

# Parallel processing settings
testcenter.navigation.max-threads=2
testcenter.navigation.thread-timeout-seconds=10
```

#### Configuration Options

| Property | Description | Default |
|----------|-------------|---------|
| `testcenter.accounts[n].username` | Username for the TestCenter account | None (required) |
| `testcenter.accounts[n].password` | Password for the TestCenter account | None (required) |
| `testcenter.accounts[n].enabled` | Whether this account should be used | false |
| `testcenter.navigation.max-threads` | Number of parallel browser sessions | 2 |
| `testcenter.navigation.thread-timeout-seconds` | How long to keep browser open after completion | 10 |

**Note:** You need at least one enabled account for the application to work properly.
