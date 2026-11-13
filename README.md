# KPOK2 - KROK Practice Test Collector

## Project Description
KPOK2 is a personal study-support application that uses browser automation to collect available KROK 1, 2, and 3 practice test items, identify unique entries, and store them for searching, organization, and further study.

This repository contains the application source code only; it does not publish a dataset of collected test questions.

## Tech Stack
- **Java 17**
- **Spring Boot** - main framework
- **Spring Data JPA** - for database operations
- **Selenium WebDriver** - for browser automation
- **Lombok** - for reducing boilerplate code
- **MapStruct** - for mapping between DTOs and entities
- **PostgreSQL** - database for storing test questions and configuration

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
- `StatisticsData` - data structure for parsing statistics

#### Services
- `TestQuestionService` - managing question storage and retrieval
- `AuthenticationService` - authentication on the TestCenter platform
- `TestParser` - coordinating the parsing process
- `TestPageParser` - parsing HTML pages using Selenium
- `TestParsingExecutor` - asynchronous execution of parsing tasks
- `TestParsingStatistics` - collecting and managing statistics about parsing sessions

#### Configurations
- `SeleniumConfig` - WebDriver configuration for Selenium
- `AsyncConfig` - thread pool configuration for asynchronous operations
- `TestCenterConfig` - TestCenter access configuration

#### API
- `ParserController` - REST API for initiating parsing and retrieving results

## System Workflow

1. **Authentication**
   - TestCenter accounts are added through the UI and stored with local AES-GCM encryption
   - Each account can be validated without starting a quiz attempt
   - Catalog operations run in a background browser and automatically self-enrol the account
     in the KROK course when no enrollment key is required

2. **Practice Item Processing**
   - The authenticated TestCenter catalog can be refreshed and searched from Navigation Settings
   - The last successful catalog is cached locally for later selection
   - The system navigates available practice sessions via Selenium
   - Locates Moodle controls by structure and URL instead of translated button captions
   - Prefers Moodle's `showall=1` review and falls back to the pagination links actually present
   - Processes single-choice, multiple-choice, and true/false items
   - Extracts one or more correct answers without relying on a localized feedback prefix
   - Applies Unicode-aware normalization to determine uniqueness across languages
   - Collects statistics about processed and newly identified items

3. **Data Storage**
   - A deterministic MD5 identity is generated from normalized question text, sorted options, and sorted correct answers
   - Both current and legacy hashes are checked so an existing database is not filled with duplicates after upgrading
   - Unique questions are stored in the database
   - An iteration succeeds only when every discovered review question was parsed
   - The attempt summary reports pages, parsed questions, new questions, duplicates, and rejected questions
   - Question type, language, quiz ID, source URL, normalization version, and the full correct-answer list are retained
   - Every distinct quiz/language origin is stored separately, so one canonical question can be linked to multiple catalog entries without duplication
   - Existing source fields are backfilled into the normalized origin table automatically on upgrade
   - Each collected Moodle attempt has an idempotent history row with completeness and question-count metrics; repeated verification preserves the initial new-question count
   - Statistics about new questions are updated

4. **Question Search and Retrieval**
   - API allows searching questions by text fragments
   - Retrieval of all saved questions is possible

5. **Statistics and Monitoring**
   - The system tracks iteration counts, newly added study items, and used accounts
   - Statistics can be viewed through a dedicated UI dialog
   - Parsing sessions are logged with detailed information

## Implementation Features

### Question Uniqueness
The system determines question uniqueness from the normalized question text, answer options, and correct answers. Normalization includes:
- Unicode NFKC normalization while preserving letters, combining marks, and numbers from any script
- Locale-independent lowercase conversion
- Removing answer labels such as `a.`, `б.`, or `2)` from comparison values
- Collapsing punctuation and repeated whitespace
- Sorting answer lists before hashing, so display order does not change identity

The previous normalization and hash format are retained for lookup only. This lets an upgraded application recognize rows collected by earlier versions.

### Asynchronous Processing
The system uses a thread pool for parallel processing of multiple practice sessions. The configuration in `AsyncConfig` allows adjusting the number of concurrent operations.

### Error Handling
A retry mechanism is implemented for failed parsing attempts. The system tracks the status of each parsing operation and can retry failed operations.

## Setup and Launch

### Prerequisites
- JDK 17 or higher
- Maven
- ChromeDriver (for Selenium)

### Configuration
The application uses Spring Boot configuration properties. Main settings can be configured through the UI:

1. **Account Management**
   - TestCenter accounts are managed through the UI interface via the menu option: TestCenter → Manage Accounts
   - You can add, edit, delete, and enable/disable accounts directly in the application
   
2. **Navigation Settings**
   - Thread configurations are managed through the UI via: TestCenter → Navigation Settings
   - Paste the full TestCenter quiz page URL, for example: `https://test.testcentr.org.ua/mod/quiz/view.php?id=173`
   - Configure the maximum number of parallel browser sessions (threads)
   - Thread timeout is set to 2 seconds by default

   The selected quiz is saved in the local application configuration. For an initial environment-based setup, you can also set `TESTCENTER_QUIZ_URL` to the same quiz page URL.

3. **Database Configuration**
   - Database settings are configured via application.properties
   - Uses PostgreSQL as the main database
   - Hibernate schema auto-update for entity management

> **Note:** Previous versions required manual editing of `application-local.properties` files, but account and navigation settings are now managed through the UI-based configuration system.

### Running the Application
```bash
mvn spring-boot:run
```

## API Endpoints

### Parser API
- `POST /api/parse?url={testUrl}` - Start parsing a test at the specified URL
- `GET /api/parse/results` - Get the results of the last parsing
- `GET /api/questions` - Get all saved questions
- `GET /api/questions/search?query={searchText}` - Search questions by text

### Navigation API 
- `POST /api/navigation/start` - Start the navigation process
- `POST /api/navigation/stop` - Stop all navigation processes
- `GET /api/navigation/status` - Get the current navigation system status

## Development Plans
- Improving the question uniqueness algorithm
- Adding support for images in questions
- Expanding search capabilities
- Optimizing parsing performance

## UI Features

The application provides a graphical user interface (JavaFX) with the following features:

### Main Window
- Control buttons for starting/stopping navigation
- Log display for real-time monitoring
- Menu options for accessing various functions

### Account Management
- Adding/editing/removing TestCenter accounts
- Enabling/disabling accounts for use in navigation
- Testing an account login and displaying its current validation status

### Navigation Settings
- Refreshing the authenticated TestCenter quiz catalog with a selected account
- Searching the cached catalog by KROK level, specialty, language, or title
- Selecting a discovered quiz or entering its `view.php?id=...` URL manually
- Configuring maximum thread count for parallel processing
- Thread timeout settings

### Parsing Statistics
- Detailed statistics about parsing operations
- Information about new questions added to the database
- Account usage overview
- Session history with timestamp, parsed pages, and found questions
