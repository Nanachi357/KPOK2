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
- `TestQuestion` - canonical question identity based on normalized context and wording
- `QuestionRevision` - one normalized set of answer options for a canonical question
- `AnswerKey` - one observed correct-answer set for a revision
- `QuestionObservation` - link to the quiz and attempt where a revision/key was seen
- `QuestionAsset` - image binary deduplicated globally by SHA-256
- `QuestionRevisionAsset` - image placement and download status within a revision
- `ParsedTestQuestionDto` - DTO for transferring question data between components
- `QuestionRecordDto` - flattened revision/key representation used by the UI and API
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
   - Treats Moodle description/information blocks as shared context rather than questions
   - Attaches each context passage to the questions that follow it
   - Extracts one or more correct answers without relying on a localized feedback prefix
   - Preserves sanitized formatted HTML, tables, MathML/TeX formulas, inline SVG, and image placements
   - Downloads Moodle images with the active authenticated session and deduplicates identical binaries
   - Detects the language from the actual question content, using catalog metadata only as a hint
   - Applies Unicode-aware normalization to determine uniqueness across languages
   - Collects statistics about processed and newly identified items

3. **Data Storage**
   - Canonical identity is generated only from normalized context and question wording
   - Option sets are stored as order-independent revisions of the canonical question
   - Correct-answer sets are stored independently from revisions
   - A different key for the same revision is retained and reported as a conflict instead of becoming a duplicate question
   - Every revision/key is linked to the quiz and Moodle attempt where it was observed
   - An iteration succeeds only when every discovered review question was parsed
   - The attempt summary reports pages, parsed questions, new questions, duplicates, and rejected questions
   - Context, question type, language, quiz ID, source URL, normalization version, and the full correct-answer list are retained
   - Rich context/question/answer HTML is stored separately from plain searchable text
   - Stored images are content-addressed by SHA-256; failed downloads remain visible as placement metadata for later retry
   - Every distinct quiz/language origin is stored separately, so one canonical question can be linked to multiple catalog entries without duplication
   - Existing source fields are backfilled into the normalized origin table automatically on upgrade
   - Each collected Moodle attempt has an idempotent history row with completeness and question-count metrics; repeated verification preserves the initial new-question count
   - Statistics about new questions are updated
   - Question containers are scanned once per document and normalized answer options are reused during answer-key detection
   - Canonical hashes for an entire parsed page are resolved with one database lookup instead of one query per question
   - Parser and persistence durations are logged in milliseconds for real-run performance monitoring

4. **Question Search and Retrieval**
   - API searches contexts, question wording, answer options, and correct answers
   - The question library returns canonical questions in bounded pages with revision, answer-key, and conflict counts
   - Retrieval returns every revision/key pair, so variants and conflicts are not hidden

5. **Portable Data Export**
   - Produces the versioned `kpok2-question-export-v1` JSON format
   - Filters by quiz, language, question type, text, and answer-key conflicts
   - Includes canonical questions, revisions, keys, origins, observations, collection attempts, rich HTML, and asset metadata
   - Keeps image binaries out of JSON; each stored asset is identified by SHA-256 and its `/api/assets/{id}` URL
   - Writes desktop exports in the background through a temporary file and atomically replaces the selected destination

6. **Safe Data Import**
   - Validates `kpok2-question-export-v1` documents before any database mutation
   - Shows a read-only preview with counts and compatibility warnings
   - Atomically merges canonical questions, revisions, answer keys, origins, observations, and attempt history
   - Matches stable hashes and source identities so importing the same file repeatedly does not create duplicates
   - Ignores source database IDs and reports unsupported image metadata separately

7. **Portable Backup and Restore**
   - Creates an unfiltered `kpok2-question-export-v1` backup with complete collection-attempt history
   - Validates and previews a backup before enabling restore
   - Rejects filtered exports in the restore workflow so partial datasets cannot be mistaken for full backups
   - Restores atomically and idempotently by using the same stable identities as the safe import workflow
   - Never includes or changes TestCenter accounts, passwords, or application settings

8. **Statistics and Monitoring**
   - The system tracks iteration counts, newly added study items, and used accounts
   - Active runs show elapsed time and aggregate attempt throughput; finite runs add an approximate remaining time after the first completed attempt
   - Unlimited runs never claim an end time and show attempts per minute instead
   - Parallel workers reserve attempts from one shared counter, so a finite run cannot exceed its configured total
   - A normal stop finishes active attempts without starting new ones; immediate interruption remains available for emergencies
   - Large runs (100 or more attempts) and unlimited runs require an explicit confirmation and explain that random delivery cannot guarantee full bank coverage
   - Statistics can be viewed through a dedicated UI dialog
   - Parsing sessions are logged with detailed information

## Implementation Features

### Question Uniqueness
The system uses a three-level identity model:
- canonical question: normalized context plus normalized question wording;
- revision: canonical question plus the normalized, sorted option set and question type;
- answer key: normalized, sorted correct-answer set.

This keeps reordered options together, preserves changed option sets as revisions, and records conflicting keys without silently treating them as unrelated questions. Normalization includes:
- Unicode NFKC normalization while preserving letters, combining marks, and numbers from any script
- Locale-independent lowercase conversion
- Removing answer labels such as `a.`, `б.`, or `2)` from comparison values
- Collapsing punctuation and repeated whitespace
- Sorting answer lists before hashing, so display order does not change identity

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
- `GET /api/assets/{id}` - Retrieve a stored question image (the question response maps content hashes to these URLs)
- `GET /api/exports/questions/options` - List available quiz, language, and question-type filters
- `GET /api/exports/questions` - Download a versioned JSON export; accepts repeated `quizId`, `language`, and `questionType` parameters plus `query`, `conflictsOnly`, and `includeAttempts`
- `POST /api/imports/questions/preview` - Validate an export and return a read-only content summary
- `POST /api/imports/questions` - Validate and atomically merge a versioned export into the local database
- `GET /api/question-library?query=&quizId=&language=&questionType=&conflictsOnly=false&page=0&size=25` - Filter, search, and page through canonical questions
- `GET /api/question-library/{questionId}` - Read all revisions, answer keys, conflicts, and origins for one canonical question

### Navigation API 
- `POST /api/navigation/start` - Start the navigation process
- `POST /api/navigation/stop-after-current` - Stop starting attempts and finish the active attempts
- `POST /api/navigation/stop` - Interrupt active navigation immediately
- `GET /api/navigation/status` - Return `running`, `stopping-after-current`, `stopping-now`, or `stopped`

## UI Features

The application provides a graphical user interface (JavaFX) with the following features:

### Main Window
- Start, graceful stop-after-current, and confirmed emergency-stop controls with live status, elapsed time, throughput, and finite-run estimates
- Non-blocking question library with full-text search, quiz/language/type/conflict filters, canonical rows, 25-item pages, revision/key/source details, and filtered JSON export
- Log display for real-time monitoring
- Filtered, background JSON export with revision and attempt history
- Preview-first, background JSON import with an explicit confirmation step
- Full backup and preview-first restore through File → Backup and Restore
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
