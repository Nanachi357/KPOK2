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
   - The system logs into TestCenter using credentials from the configuration

2. **Test Parsing**
   - The system opens the test URL via Selenium
   - Parses questions, answer options, and correct answers
   - Normalizes question text to determine uniqueness
   - Collects statistics about the parsing process and questions found

3. **Data Storage**
   - An MD5 hash of the normalized text is generated for each question
   - Question uniqueness is checked by hash
   - Unique questions are stored in the database
   - Statistics about new questions are updated

4. **Question Search and Retrieval**
   - API allows searching questions by text fragments
   - Retrieval of all saved questions is possible

5. **Statistics and Monitoring**
   - The system tracks iteration counts, newly added tests, and used accounts
   - Statistics can be viewed through a dedicated UI dialog
   - Parsing sessions are logged with detailed information

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
The application uses Spring Boot configuration properties. Main settings can be configured through the UI:

1. **Account Management**
   - TestCenter accounts are managed through the UI interface via the menu option: TestCenter → Manage Accounts
   - You can add, edit, delete, and enable/disable accounts directly in the application
   
2. **Navigation Settings**
   - Thread configurations are managed through the UI via: TestCenter → Navigation Settings
   - Configure the maximum number of parallel browser sessions (threads)
   - Thread timeout is set to 2 seconds by default

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

### Navigation Settings
- Configuring maximum thread count for parallel processing
- Thread timeout settings

### Parsing Statistics
- Detailed statistics about parsing operations
- Information about new questions added to the database
- Account usage overview
- Session history with timestamp, parsed pages, and found questions
