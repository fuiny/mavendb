CREATE SCHEMA `mvnrepos` IF NOT EXISTS DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin ;
USE `mvnrepos`;


DROP TABLE IF exists `artifactinfo`;
CREATE TABLE `artifactinfo` (
  `uinfo_md5`                    binary(16)                           NOT NULL COMMENT 'MD5 of `uinfo`',

  `major_version`                   int                           DEFAULT NULL COMMENT 'Generated from `artifact_version`, the most left part of the version',
  `version_seq`                  bigint      NOT NULL             DEFAULT '0'  COMMENT 'Generated from `artifact_version`, sequence number for the version',
  `uinfo_length`                    int                           DEFAULT NULL COMMENT 'ArtifactInfo.getUinfo().length()',                 -- 2023.02.12  Max    175
  `classifier_length`               int                           DEFAULT NULL COMMENT 'ArtifactInfo.getClassifier().length()',            -- 2023.02.12  Max     54

  `signature_exists`                int                           DEFAULT NULL COMMENT 'ArtifactInfo.getSignatureExists()\norg.apache.maven.index.ArtifactAvailability: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',
  `sources_exists`                  int                           DEFAULT NULL COMMENT 'ArtifactInfo.getSourcesExists()\norg.apache.maven.index.ArtifactAvailability: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',
  `javadoc_exists`                  int                           DEFAULT NULL COMMENT 'ArtifactInfo.getJavadocExists()\norg.apache.maven.index.ArtifactAvailability: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',

  `json`                           json                           DEFAULT NULL COMMENT 'toJson(ArtifactInfo)',                             -- 2023.01.01  Max 54,930

  PRIMARY KEY (`uinfo_md5`)
) ENGINE=InnoDB COLLATE=utf8mb4_bin COMMENT 'Maven Repos ArtifactInfo, from https://github.com/apache/maven-indexer/blob/master/indexer-core/src/main/java/org/apache/maven/index/ArtifactInfo.java';


DROP TABLE IF exists `gav`;
CREATE TABLE         `gav` (
  `uinfo_md5`                    binary(16)                           NOT NULL COMMENT 'From [artifactinfo]-`uinfo_md5`',
  `uinfo_length`                    int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`uinfo_length`',

  `group_id`                    varchar(254)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.groupId"`',         -- 2023.02.12  Max 93
  `artifact_id`                 varchar(254)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.artifactId"`',      -- 2023.02.12  Max 87
  `artifact_version`            varchar(128)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.version"`',         -- 2023.02.12  Max 94

  `major_version`                   int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`major_version`',
  `version_seq`                  bigint                               NOT NULL COMMENT 'From [artifactinfo]-`version_seq`',

  `last_modified`              DATETIME                           DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.lastModified"`',
  `size`                         bigint                           DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.size"`',
  `sha1`                           char(  40) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.sha1"`',

  `signature_exists`                int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.signatureExists"` or From [artifactinfo]-`signature_exists`, Values: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',
  `sources_exists`                  int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.sourcesExists"`   or From [artifactinfo]-`sources_exists`,   Values: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',
  `javadoc_exists`                  int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.javadocExists"`   or From [artifactinfo]-`javadoc_exists`,   Values: NOT_PRESENT(0), PRESENT(1), NOT_AVAILABLE(2)',

  `classifier`                  varchar( 128) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.classifier"`',
  `classifier_length`               int                           DEFAULT NULL COMMENT 'From [artifactinfo]-`classifier_length`',
  `file_extension`              varchar( 254) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.fileExtension"`',   -- 2023.02.12  Max    113
  `packaging`                   varchar( 254) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.packaging"`',       -- 2023.02.12  Max    113
  `name`                        varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.name"`',            -- 2023.02.12  Max    190
  `description`                 MEDIUMTEXT    COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'From [artifactinfo]-`json->>"$.description"`',     -- 2023.02.12  Max 53,221

  PRIMARY KEY (`uinfo_md5`),
  KEY `index_gav` (`group_id`,`artifact_id`,`artifact_version`)
) ENGINE=InnoDB COLLATE=utf8mb4_bin COMMENT='Groups Artifact Version';


DROP TABLE IF exists `g`;
CREATE TABLE         `g` (
  `group_id`                    varchar(254)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.groupId"`',

  `artifact_version_counter`        int                           DEFAULT NULL,
  `major_version_counter`           int                           DEFAULT NULL,
  `version_seq_max`              bigint                           DEFAULT NULL,
  `last_modified_max`              DATE                           DEFAULT NULL,
  `group_id_left1`              varchar(128) COLLATE utf8mb4_bin GENERATED ALWAYS AS (substring_index(`group_id`,'.',1)) VIRTUAL,
  `group_id_left2`              varchar(254) COLLATE utf8mb4_bin GENERATED ALWAYS AS (substring_index(`group_id`,'.',2)) VIRTUAL,
  `group_id_left3`              varchar(254) COLLATE utf8mb4_bin GENERATED ALWAYS AS (substring_index(`group_id`,'.',3)) VIRTUAL,
  `group_id_left4`              varchar(254) COLLATE utf8mb4_bin GENERATED ALWAYS AS (substring_index(`group_id`,'.',4)) VIRTUAL,

  PRIMARY KEY (`group_id`),
  KEY `index_group_id_left1` (`group_id_left1`),
  KEY `index_group_id_left2` (`group_id_left2`),
  KEY `index_group_id_left3` (`group_id_left3`),
  KEY `index_group_id_left4` (`group_id_left4`)
) ENGINE=InnoDB COLLATE=utf8mb4_bin COMMENT='Groups';


DROP TABLE IF exists `ga`;
CREATE TABLE         `ga` (
  `group_id`                    varchar(254)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.groupId"`',
  `artifact_id`                 varchar(254)  COLLATE utf8mb4_bin     NOT NULL COMMENT 'From [artifactinfo]-`json->>"$.artifactId"`',

  `artifact_version_counter`        int                           DEFAULT NULL,
  `major_version_counter`           int                           DEFAULT NULL,
  `version_seq_max`              bigint                           DEFAULT NULL,
  `last_modified_max`              DATE                           DEFAULT NULL,

  PRIMARY KEY (`group_id`,`artifact_id`)
) ENGINE=InnoDB COLLATE=utf8mb4_bin COMMENT='Groups Artifact';


--
-- Views
--

DROP VIEW IF EXISTS v_ga2ga;
CREATE VIEW v_ga2ga AS
SELECT
  ga.group_id,
  ga.artifact_id,
  IFNULL(ai.name, ga.artifact_id)                      AS name,
  IFNULL(ai.description, ga.artifact_id)               AS description

FROM ga
LEFT JOIN gav ai
  ON  ga.group_id        = ai.group_id
  AND ga.artifact_id     = ai.artifact_id
  AND ga.version_seq_max = ai.version_seq
;


DROP VIEW IF EXISTS v_artifactinfo2gav;
CREATE VIEW         v_artifactinfo2gav AS
SELECT
  group_id,
  artifact_id,
  artifact_version,
  major_version,
  version_seq,
  uinfo_md5                                            AS ref_md5,
  last_modified                                        AS mvn_last_modified,
  2                                                    AS location_type,
  concat('mvn dependency:copy -U -DoutputDirectory=. -Dartifact=',
    if(isnull(classifier),
        concat(group_id, ':', artifact_id, ':', artifact_version,':', file_extension),
        concat(group_id, ':', artifact_id, ':', artifact_version,':', file_extension, ':', classifier)
    ))                      AS location,
  size,
  classifier,
  file_extension,
  packaging,
  name,
  description

FROM gav
WHERE ( classifier is null OR classifier = 'bin' )
  AND       file_extension    IN (select extension from binarydocjvmadm.extension)
  AND       artifact_version  IS NOT NULL
  AND lower(artifact_version) NOT LIKE '%alpha%'
  AND lower(artifact_version) NOT LIKE '%beta%'
  AND lower(artifact_version) NOT LIKE '%build%'
  AND lower(artifact_version) NOT LIKE '%cr%'
  AND lower(artifact_version) NOT LIKE '%custom%'
  AND lower(artifact_version) NOT LIKE '%dev%'
  AND lower(artifact_version) NOT LIKE '%draft%'
  AND lower(artifact_version) NOT LIKE '%incubating%'
  AND lower(artifact_version) NOT LIKE '%m%'
  AND lower(artifact_version) NOT LIKE '%nightly%'
  AND lower(artifact_version) NOT LIKE '%post%'
  AND lower(artifact_version) NOT LIKE '%pre%'
  AND lower(artifact_version) NOT LIKE '%preview%'
  AND lower(artifact_version) NOT LIKE '%rc%'
  AND lower(artifact_version) NOT LIKE '%snapshot%'
  AND lower(artifact_version) NOT LIKE '%sp%'
;
