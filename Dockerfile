FROM openjdk:8-alpine

COPY target/uberjar/art-bot.jar /art-bot/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/art-bot/app.jar"]
