# Dos etapas: la primera compila con el JDK completo, la segunda se queda solo con el
# jar y un JRE. La imagen final no lleva ni Maven ni el código fuente.
FROM eclipse-temurin:25-jdk AS construccion
WORKDIR /app

# Primero solo lo que define las dependencias: mientras el pom no cambie, Docker
# reutiliza esta capa y no vuelve a bajar medio Maven Central en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Los tests ya corrieron en el CI antes de llegar aquí; repetirlos en el build solo
# haría el despliegue más lento.
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

# Sin usuario root: si alguien logra ejecutar algo dentro del contenedor, que sea poco
RUN useradd --system --home /app aplicacion
USER aplicacion

COPY --from=construccion /app/target/*.jar app.jar

# MaxRAMPercentage y no -Xmx: la memoria del plan de Render puede cambiar y el
# porcentaje se adapta solo. El puerto lo decide Render con la variable PORT.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
