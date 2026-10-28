FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
WORKDIR /app
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENV TZ="Europe/Oslo"
EXPOSE 8080

# Sett UID/GID som ikke-root
USER 1000:1000

CMD ["-jar","app.jar"]