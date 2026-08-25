# TourGuide

TourGuide is a Spring Boot application designed to provide users with information about nearby tourist attractions, reward points and personalized trip offers.

This project focuses on **fixing existing issues, improving application performance and implementing a continuous integration pipeline**.

## Project objectives

The main objectives of this project were to:

- Fix the `ConcurrentModificationException` occurring during concurrent operations.
- Correct the nearby attractions functionality to return the five closest attractions with the required information.
- Improve location tracking performance for a large number of users.
- Improve reward calculation performance.
- Ensure that the existing tests pass successfully.
- Implement a Continuous Integration (CI) pipeline.

## Technologies

- Java 17
- Spring Boot
- Maven
- JUnit
- Git
- GitHub Actions

The application also uses the external libraries provided with the original project:

- `gpsUtil`
- `RewardCentral`
- `TripPricer`

## Main improvements

### Concurrent data access

The application was experiencing a `ConcurrentModificationException` when collections could be modified while being iterated.

Thread-safe collections such as `CopyOnWriteArrayList` are used to allow safe concurrent access to the affected data.

### Nearby attractions

The nearby attractions functionality was corrected to return the **five closest attractions** to the user.

The response includes:

- attraction name;
- attraction latitude and longitude;
- user latitude and longitude;
- distance between the user and the attraction;
- potential reward points.

### Performance optimization

Location tracking and reward calculation were optimized to support a large number of users.

The main optimizations include:

- `ExecutorService` with a fixed thread pool;
- asynchronous processing with `CompletableFuture`;
- batch processing to limit the number of tasks created simultaneously;
- caching of the attraction list;
- use of `HashSet` for efficient duplicate reward detection;
- early termination of unnecessary searches.

## Performance results

The application was tested with up to **100,000 users**.

| Test | Requirement | Improved result |
|---|---:|---:|
| Location tracking | < 15 min | ~60 s |
| Reward calculation | < 20 min | ~565 s (9 min 25 s) |

The performance tests are available in `TestPerformance`.

## Running the tests

Run all tests with:

```bash
mvn test
```

A specific performance test can also be executed individually:

```bash
mvn -Dtest=TestPerformance#highVolumeTrackLocation test
```

or:

```bash
mvn -Dtest=TestPerformance#highVolumeGetRewards test
```

## Continuous Integration

A GitHub Actions workflow is included in the repository.

The CI pipeline:

1. checks out the repository;
2. configures Java 17;
3. installs the local project dependencies;
4. compiles the application;
5. runs the automated tests;
6. builds the application JAR;
7. publishes the generated artifact.

The local `gpsUtil`, `RewardCentral` and `TripPricer` libraries are installed during the workflow because they are not available from Maven Central.

## Build

To build the project:

```bash
mvn clean package
```

The generated JAR is available in the `target` directory.

## Project structure

```text
TourGuide
├── src
│   ├── main
│   │   └── java
│   └── test
│       └── java
├── libs
├── .github
│   └── workflows
├── pom.xml
└── README.md
```

## Author

Project completed as part of the OpenClassrooms Java Developer path
