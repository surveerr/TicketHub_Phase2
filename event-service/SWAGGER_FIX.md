# Swagger fix

This service is configured for JDK 17 and Spring Boot 4.1.1.

Springdoc has been changed to 3.1.1 because springdoc 3.x is the line compatible with Spring Boot 4.

After replacing the service:

1. Delete the `target` folder.
2. Reload Maven in IntelliJ.
3. Build with JDK 17:
   `mvn clean package -DskipTests`
4. Start the newly built jar.
5. Open:
   - http://localhost:8081/swagger-ui.html
   - http://localhost:8081/swagger-ui/index.html
   - http://localhost:8081/v3/api-docs

Swagger/OpenAPI paths are permitted by Spring Security, and the JWT filter explicitly skips them.
