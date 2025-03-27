# IJ Commit Tracer

An IntelliJ IDEA plugin that analyzes Git commit history and integrates with YouTrack issue tracking.

## Features

- **Commit History Visualization**: Lists all commits in Git repositories within your project
- **YouTrack Integration**: Automatically extracts and links YouTrack ticket references from commit messages
- **Commit Analytics**: Provides statistics about commits by author, referenced tickets, and more
- **Issue Highlighting**: Special tracking for blocker and regression tickets
- **Secure Authentication**: Secure storage of YouTrack API tokens using IntelliJ's credential system
- **Test Impact Analysis**: Track which commits modify test files with visual indicators

## Installation

1. Download the plugin from the JetBrains Marketplace
2. Install via IntelliJ IDEA: Settings/Preferences → Plugins → Install from disk
3. Restart IntelliJ IDEA

## Configuration

1. Go to Commit Tracer → Configure YouTrack Token
2. Enter your YouTrack API token
3. The token will be securely stored for future use

## Usage

### Viewing Commit History

1. Go to Commit Tracer → List Repository Commits
2. Filter commits by date range if needed
3. View statistics by author, including commit counts and ticket references
4. Use the search field to filter commits by message content

### Commit Details

The plugin provides detailed information for each commit:
- Full commit message
- Author information
- Date and time
- Repository and branch information
- Test file impact (whether the commit modified test files)
- Changed files with status (added, modified, deleted)

### YouTrack Integration

The plugin automatically:
- Extracts YouTrack ticket IDs from commit messages (e.g., PROJ-1234)
- Fetches ticket details from YouTrack including summary and tags
- Highlights tickets marked as blockers or regressions

### Author Statistics

View detailed statistics for each committer:
- Total commit count
- First and last commit dates
- Active days count
- Commits per day average
- Test coverage percentage
- Referenced YouTrack tickets

### Clearing Stored Token

If you need to change or remove your YouTrack token:
1. Go to Commit Tracer → Clear YouTrack Token

## Development

This plugin is built using:
- Kotlin 1.9.25
- IntelliJ Platform SDK (2024.2.5)
- Gradle build system
- Git4Idea plugin for Git integration
- JSON library for YouTrack API integration

### Test File Detection

The plugin automatically detects test files based on:
- Files in directories containing "test" or "tests"
- Files with "Test" or "Tests" in their name
- Files ending with common test extensions (Test.kt, Test.java, Tests.kt, Tests.java, Spec.kt, Spec.java, _test.go)
- Explicitly excludes .iml and .bazel files from being treated as tests

## License

This plugin is distributed under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0).

## Support

For support, please create an issue in the plugin's repository.