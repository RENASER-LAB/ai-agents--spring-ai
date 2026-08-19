FROM eclipse-temurin:25-jdk AS construccion
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -DskipTests package
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --home /app aplicacion
USER aplicacion
COPY --from=construccion /app/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
