# Maven repository to DB

This application will scan all `maven` repos items and store them to `MySQL` database.

## Build and Run Natively

Requriments

* OpenJDK `17` or later
* Maven `3.9.3` or later

Build the Source Code
* (Optional) Delete current data - `rm -rf dist/ target/`
* `mvn clean package install dependency:tree org.codehaus.mojo:versions-maven-plugin:2.16.0:display-dependency-updates`

How to Run the Tool
* There will be an `zip` file generated inside `dist` folder, Unzip the file
* Come to the `etc` folder, edit the `config.properties` file
  * Modify the parameter `jakarta.persistence.jdbc.url` for the MySQL hostname
  * Modify the parameter `jakarta.persistence.jdbc.user` for the username
  * Modify the parameter `jakarta.persistence.jdbc.password` for the password
* Come to the `bin` folder, run either of the following commands
  * `bin $` `./run.sh central`
  * `bin $` `./run.sh spring`
* Where
  * `central` match to `repos-central.properties` file for maven central repos
  * `spring` match to `repos-spring.properties` file for spring repository

## Build and Run via Docker

Build Docker Image
* `sudo docker build -t fuiny/mavendb .`

Check the images
* `sudo docker images`

Environment variables
* `MAVENDB_MYSQL_HOST` - MySQL DB Hostname or IP
* `MAVENDB_MYSQL_PORT` - MySQL DB TCP Port
* `MAVENDB_MYSQL_USER` - MySQL DB Username
* `MAVENDB_MYSQL_PASS` - MySQL DB Password
* `MAVENDB_ARGS` - The `mavendb` jar file input parameters, samples as bellow
  * `-r central` - Run against `Central` Maven
  * `-r spring` - Run against `Spring` Maven

Run
* Run the Docker with default environment variables
  * `sudo docker run -it --rm fuiny/mavendb`
* Run against an MySQL Host name
  * `sudo docker run -e MAVENDB_MYSQL_HOST=192.168.1.110 -it --rm fuiny/mavendb`
* Run against an MySQL Host name and Central Maven
  * `sudo docker run -e MAVENDB_MYSQL_HOST=192.168.1.110 -e MAVENDB_ARGS='-r central' -it --rm fuiny/mavendb`


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

