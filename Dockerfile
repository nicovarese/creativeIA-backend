# =====================================================================
#  Multi-stage Dockerfile para creativeIA backend
#
#  Stage 1 (build):   Maven 3.9 + Eclipse Temurin 21 → compila a .jar
#  Stage 2 (runtime): Eclipse Temurin 21 JRE Alpine → corre el .jar
#
#  Build local:
#     docker build -t creativeia-backend .
#  Run local:
#     docker run -p 8080:8080 --env-file .env creativeia-backend
# =====================================================================

# -----------------------------------------------------------------
#  Stage 1: build
# -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copiamos primero el wrapper de Maven + pom.xml para que la capa de
# dependencias quede cacheada y solo se rehaga si cambia el pom.
COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline -DskipTests

# Ahora sí, copiamos el código y compilamos.
COPY src src
RUN ./mvnw -B -DskipTests package

# -----------------------------------------------------------------
#  Stage 2: runtime
# -----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Fuentes necesarias para Graphics2D (MockImageProviderAdapter usa drawString).
# Alpine no incluye fontconfig por default y los métodos de fuente revientan.
RUN apk add --no-cache fontconfig ttf-dejavu

WORKDIR /app

# Copiamos el jar generado, renombrándolo a app.jar para tener un
# ENTRYPOINT estable independiente de la versión del artifact.
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
