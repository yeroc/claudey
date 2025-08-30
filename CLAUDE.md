# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Maven CLI application using Quarkus framework with LangChain4j for AI integration. The project uses Java 21 and follows standard Maven directory structure. The main application implements QuarkusApplication interface for command-line execution.

- **Group ID**: org.geekden
- **Artifact ID**: test-app
- **Java Version**: 21
- **Framework**: Quarkus 3.8.1
- **AI Library**: LangChain4j 0.29.1
- **Main Class**: org.geekden.MainApplication

## Development Commands

### Build and Test
```bash
mvn clean compile          # Compile the project
mvn clean package          # Package the application
mvn test                   # Run tests
mvn quarkus:dev            # Run in development mode with hot reload
```

### Running the Application
```bash
mvn quarkus:dev            # Development mode with live reload
java -jar target/quarkus-app/quarkus-run.jar  # Run packaged application
```

## Code Style

- Always use 2 space indents, no tabs
- Follow standard Java naming conventions
- Use JUnit 5 for testing

## Dependencies

The project includes:
- LangChain4j OpenAI integration
- JUnit 5 for testing
- Quarkus JUnit5 for integration testing
- the `quarkus` cli tool is installed and should be used to add quarkus extensions etc as required