# Maven Repository to DB

This application will scan all maven repos items and store them to MySQL database.

## Build

Build the Source Code
* `mvn clean package install`

Build and Validate
* `mvn clean package install javadoc:aggregate jxr:jxr checkstyle:checkstyle-aggregate dependency:tree versions:display-dependency-updates`

## Run the Tool

* `bin $` `./run.sh central`
* `bin $` `./run.sh spring`
  * Where `central` match to `repos-central.properties` file for maven central repos
  * And `spring` match to `repos-spring.properties` file for spring repository
