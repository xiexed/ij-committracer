# IJ Commit Tracer

An IntelliJ IDEA plugin that analyzes Git commit history and integrates with YouTrack issue tracking.

## Features

- **Commit History Visualization**: Lists all commits in Git repositories within your project
- **YouTrack Integration**: Automatically extracts and links YouTrack ticket references from commit messages
- **Commit Analytics**: Provides statistics about commits by author, referenced tickets, and more
- **Issue Highlighting**: Special tracking for blocker and regression tickets
- **Secure Authentication**: Secure storage of YouTrack API tokens using IntelliJ's credential system

## Installation

1. Download the plugin from the JetBrains Marketplace
2. Install via IntelliJ IDEA: Settings/Preferences → Plugins → Install from disk
3. Restart IntelliJ IDEA

## Configuration

### Method 1: Through the UI
1. Go to Tools → Configure YouTrack Token
2. Enter your YouTrack API token
3. The token will be securely stored for future use

### Method 2: Using .env File
For development or to avoid entering tokens repeatedly, you can create a `.env` file in the project root:

```
# YouTrack API Configuration
YOUTRACK_API_TOKEN=your_youtrack_token_here
YOUTRACK_API_URL=https://youtrack.jetbrains.com/api

# HiBob API Configuration
HIBOB_SERVICE_USER_ID=your_service_user_id_here
HIBOB_API_TOKEN=your_hibob_token_here
HIBOB_API_URL=https://api.hibob.com/v1
```

The plugin will automatically detect and use these credentials if the file exists, falling back to the credential store if not found.

## HiBob Integration Command-Line Tester

For testing the HiBob API integration, the plugin includes an interactive command-line tool:

1. Run the main method in `HiBobCliTester` class
2. The tool provides an interactive menu to:
   - Specify a custom .env file path
   - Add email addresses to test
   - View the configured test emails
   - Run the integration test and view results

Example usage with custom .env file and emails:
```
$ ./gradlew runIde

# Then in HiBobCliTester:
# 1. Choose option 1 to set .env path
# 2. Choose option 2 to add emails to test
# 3. Choose option 5 to run the test
```

The tester shows employee information including name, title, team, and manager for each email tested.

## Usage

### Viewing Commit History

1. Go to Tools → List Commits
2. Filter commits by date range if needed
3. View statistics by author, including commit counts and ticket references

### YouTrack Integration

The plugin automatically:
- Extracts YouTrack ticket IDs from commit messages (e.g., PROJ-1234)
- Fetches ticket details from YouTrack including summary and tags
- Highlights tickets marked as blockers or regressions

### Clearing Stored Token

If you need to change or remove your YouTrack token:
1. Go to Tools → Clear YouTrack Token

## Development

This plugin is built using:
- Kotlin
- IntelliJ Platform SDK
- Gradle build system

## License

[Specify the license here]

## Support

[Specify support contact information]