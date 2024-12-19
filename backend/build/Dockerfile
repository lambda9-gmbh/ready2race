FROM openjdk:21-slim
EXPOSE 8080:8080
RUN mkdir /app
COPY ./app.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]