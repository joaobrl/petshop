# Este Dockerfile espera ser buildado com o contexto na raiz do repositório
# (build.context: . no docker-compose.yml), porque booking-service depende
# da lib compartilhada petshop-commons, que não é publicada em nenhum repositório
# remoto — precisa ser buildada e instalada no .m2 local antes.

# Etapa 1: Build da lib compartilhada petshop-commons
FROM maven:3.9-eclipse-temurin-21 AS commons-build
WORKDIR /commons
COPY petshop-commons/pom.xml .
COPY petshop-commons/src ./src
RUN mvn clean install -DskipTests

# Etapa 2: Build do booking-service, usando o petshop-commons instalado acima
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY --from=commons-build /root/.m2 /root/.m2
COPY booking-service/pom.xml .
COPY booking-service/src ./src
RUN mvn clean package -DskipTests

# Etapa 3: Execução
FROM eclipse-temurin:21-jre
RUN addgroup --system spring && adduser --system --ingroup spring spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
