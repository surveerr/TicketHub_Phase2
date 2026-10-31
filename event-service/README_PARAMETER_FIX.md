# Event Service - REST Parameter Fix

## Required local environment
- JDK: 17
- Maven: use the Maven wrapper/IDE Maven configured to JDK 17
- Spring Boot: 4.1.1 (as requested)
- Java release/source/target: 17

## What was fixed
Every Spring MVC `@PathVariable` and `@RequestParam` in the event-service controllers now has an explicit parameter name. This prevents:

`Name for argument of type [java.lang.Long] not specified, and parameter name information not available via reflection.`

This works even when the application is compiled without Java `-parameters` metadata.

## Controller files changed
- `src/main/java/com/tickethub/eventshow/controller/InternalShowController.java`
- `src/main/java/com/tickethub/eventshow/controller/SeatController.java`
- `src/main/java/com/tickethub/eventshow/controller/ShowController.java`
- `src/main/java/com/tickethub/eventshow/controller/VenueController.java`
- `EventController.java` was already using explicit names and was retained.

## Clean rebuild
In IntelliJ:
1. Project SDK = JDK 17.
2. Maven Runner JDK = 17.
3. Reload Maven.
4. Build > Rebuild Project.

Or:

```bash
mvn clean package -DskipTests
```

Then stop the old event-service process and start the newly built JAR. Do not run an old JAR from a previous `target` directory.

## Postman examples
```http
GET http://localhost:8081/api/v1/shows
GET http://localhost:8081/api/v1/shows/1
GET http://localhost:8081/api/v1/shows/1/tickets
GET http://localhost:8081/api/v1/shows/1/seats
GET http://localhost:8081/api/v1/screens/3/seats
GET http://localhost:8081/api/v1/venues
GET http://localhost:8081/api/v1/enums
GET http://localhost:8081/internal/v1/shows/1
```
