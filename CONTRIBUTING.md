# Contributing Guidelines

## Commit Message Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages. This leads to more readable messages that are easy to follow when looking through the project history.

### Commit Message Format
Each commit message consists of a **type** and a **subject**:

`<type>: <subject>`

#### Types
* `feat`: New feature
* `fix`: Bug fix
* `docs`: Documentation only changes
* `style`: Changes that do not affect the meaning of the code (formatting, etc)
* `refactor`: Code change that neither fixes a bug nor adds a feature
* `test`: Adding missing tests or correcting existing tests
* `chore`: Changes to the build process or auxiliary tools

#### Examples
* `feat: add test batch processing`
* `fix: resolve null pointer in parser`
* `docs: update README with setup instructions`
* `refactor: extract parsing logic to separate class`
* `test: add unit tests for parser`
* `chore: update Spring Boot version`