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
  * Where `192.168.1.110` is the MySQL DB Host Address
  * Where `MAVENDB_MYSQL_PORT`, `MAVENDB_MYSQL_USER`, `MAVENDB_MYSQL_PASS`, `MAVENDB_ARGS` are the default values
* Run against an MySQL Host name and Central Maven
  * `sudo docker run -e MAVENDB_MYSQL_HOST=192.168.1.110 -e MAVENDB_ARGS='-r central' -it --rm fuiny/mavendb`


## Run via Pre-Configured Docker-Compose

A Docker Compose file has been configured
* [docker-compose.yml](docker-compose.yml)

Step 1. Config
- Modify the passwords set in the [.env](.env) file based on security requirements
- Modify the `innodb_buffer_pool_size` in [docker-compose.yml](docker-compose.yml) based on hardware

```
 Buffer Pool Settings based on OS RAM Size
   OS RAM :  innodb_buffer_pool_size , innodb_buffer_pool_instances
    16 GB                        10G , 10
    32 GB                        20G , 20
    64 GB                        40G , 20
   128 GB                        80G , 20
```

Step 2. Run
- For `Ubuntu`/`Linux` users
  - [Install Docker](https://docs.docker.com/engine/install/ubuntu/)
  - Execute script [docker-compose-run.sh](docker-compose-run.sh)
    - `./docker-compose-run.sh`
- For MacOS Users
  - [Install Docker Desktop](https://docs.docker.com/desktop/install/mac-install/)
  - Execute script [docker-compose-run.sh](docker-compose-run.sh)
    - `./docker-compose-run.sh`
- For Windows Users
  - [Install Docker Desktop](https://docs.docker.com/desktop/install/windows-install/)
  - Make sure the [docker memory resource limit](https://stackoverflow.com/questions/43460770/docker-windows-container-memory-limit) is bigger than the MySQL `innodb_buffer_pool_size`
    - Example: on a 64GB RM Windows laptop, set `--innodb_buffer_pool_size=24G` will work for maven central scan
  - Execute script [docker-compose-run.ps1](docker-compose-run.ps1)
    - `powershell -ExecutionPolicy Bypass -File .\docker-compose-run.ps1`


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

