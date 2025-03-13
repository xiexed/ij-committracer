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

1. Go to Tools → Configure YouTrack Token
2. Enter your YouTrack API token
3. The token will be securely stored for future use

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