# Maven repository to DB

This application will scan all `maven` repos items and store them to `MySQL` database.

## Build and Run Natively

Requriments

* Ubuntu Linux
* OpenJDK `17` or later
* Maven `3.9.3` or later

Build the Source Code
* `mvn clean package install dependency:tree versions:display-dependency-updates`

Run the Tool
* There will be an `zip` file generated inside `dist` folder
* Unzip the file and come to the `bin` folder
* Run either of the following commands
  * `bin $` `./run.sh central`
  * `bin $` `./run.sh spring`
* Where
  * `central` match to `repos-central.properties` file for maven central repos
  * `spring` match to `repos-spring.properties` file for spring repository

## Build and Run via Docker

Build Docker Image
* `sudo docker build -t fuiny/mavendb .`

Run the Docker
* `sudo docker run -it --rm fuiny/mavendb`


## Publish Site (Internal Only)

Maven Settings
* Edit `conf/settings.xml`
* Add Server section, where
  * `username` is the github login user
  * `password` is the github user's token

```
<server>
  <id>github.com</id>
  <username></username>
  <password></password>
</server>
```

Publish site
* `mvn clean site site:stage scm-publish:publish-scm`

