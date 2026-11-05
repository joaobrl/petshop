# Diferente dos outros serviços, gateway-service não depende de
# petshop-commons (só roteamento + rate limit; valida o JWT direto com jjwt) -
# por isso o build aqui é single-stage, sem a etapa de instalar a lib
# compartilhada no .m2 local primeiro.

# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY gateway-service/pom.xml .
COPY gateway-service/src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Execução
FROM eclipse-temurin:21-jre
RUN addgroup --system --gid 10001 spring && adduser --system --uid 10001 --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
