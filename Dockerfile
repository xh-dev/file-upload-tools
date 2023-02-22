FROM maven:3-amazoncorretto-17 as build
COPY . /app/
WORKDIR /app
RUN mvn clean package

FROM amazoncorretto:17
COPY --from=build /app/target/*.jar /app/app.jar
WORKDIR /app
CMD ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]