# HiBob CLI Tester

A standalone command-line tool for testing HiBob API integration without requiring the IntelliJ IDEA environment.

## Usage

### Running from IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Navigate to `src/main/kotlin/com/example/ijcommittracer/playground/RunHiBobCliTester.kt`
3. Click the green "Run" icon next to the `main` method
4. Follow the interactive menu in the console

### Running from Command Line

Compile the project and run:

```
# From project root directory
./gradlew compileKotlin

# Then run with Java
java -cp "build/classes/kotlin/main:lib/*" com.example.ijcommittracer.playground.RunHiBobCliTester
```

### Command-Line Arguments

You can provide command-line arguments to set the .env file path and test emails:

```
java -cp "build/classes/kotlin/main:lib/*" com.example.ijcommittracer.playground.RunHiBobCliTester /path/to/.env email1@example.com email2@example.com
```

## Setting Up Your .env File

Create a `.env` file with the following contents:

```
# HiBob API Configuration
HIBOB_SERVICE_USER_ID=your_service_user_id
HIBOB_API_TOKEN=your_token
HIBOB_API_URL=https://api.hibob.com/v1
```

## Features

- Interactive menu-driven interface
- Configure .env file path
- Add multiple email addresses to test
- View formatted results with employee information
- Uses HiBob API Basic authentication
- No IntelliJ platform dependencies required

## How It Works

This tool:
1. Reads credentials from your .env file
2. Forms Basic authentication with `service-user-id:token` format
3. Makes API calls to HiBob's `/people` endpoint
4. Parses and displays employee information