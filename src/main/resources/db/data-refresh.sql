--
-- Correct data records
--
-- Maven Central index data has bugs, we will fix theose data we are using whenver found.
--

USE mvnrepos;
select concat(now(), ' Started');

--
-- Refresh the counter tables after updated the latest tgav
--

-- Refresh data for table gav
--  Note. We did not do string cut here, so the SQL will fail if the data is too long
--        We need to watch out the errors in case happens
TRUNCATE TABLE gav;
INSERT INTO    gav(
  uinfo_md5,
  uinfo_length,

  group_id,
  artifact_id,
  artifact_version,

  major_version,
  version_seq,

  last_modified,
  `size`,
  sha1,

  signature_exists,
  sources_exists,
  javadoc_exists,

  classifier,
  classifier_length,
  file_extension,
  packaging,
  `name`,
  description
)
SELECT
  uinfo_md5,
  uinfo_length,

  json->>"$.groupId"                              AS group_id,
  json->>"$.artifactId"                           AS artifact_id,
  json->>"$.version"                              AS artifact_version,

  major_version,
  version_seq,

  FROM_UNIXTIME(json->>"$.lastModified" / 1000)   AS last_modified,
  json->>"$.size"                                 AS `size`,
  json->>"$.sha1"                                 AS sha1,

  signature_exists,
  sources_exists,
  javadoc_exists,

  json->>"$.classifier"                           AS classifier,
  classifier_length,
  json->>"$.fileExtension"                        AS file_extension,
  json->>"$.packaging"                            AS packaging,
  json->>"$.name"                                 AS `name`,
  json->>"$.description"                          AS description
FROM artifactinfo
;
select concat(now(), ' Table gav refresh data finished');


-- Fix data for: `group_id` = 'org.apache.tomcat' AND `artifact_id` = 'tomcat'
--   Problem        : after 2020-12-03/9.0.41 the ArtifactInfo only contains record for 'zip.sha512' but no data for the "zip" file
--   Problem Example: https://repo1.maven.org/maven2/org/apache/tomcat/tomcat/9.0.41/  'tomcat-9.0.41.zip.sha512' exits but 'tomcat-9.0.41.zip' is missing
--   Solution       : We convert the current 'zip.sha512' record as 'zip'

UPDATE gav
SET   file_extension = 'zip'
WHERE group_id       = 'org.apache.tomcat'
  AND artifact_id    = 'tomcat'
  AND file_extension = 'zip.sha512'
;
select concat(now(), ' Table gav Fix org.apache.tomcat/tomcat/zip.sha512 finished');


-- Refresh data for table g
TRUNCATE TABLE g;
INSERT INTO    g (
  group_id,
  artifact_version_counter,
  major_version_counter,
  version_seq_max,
  last_modified_max           )
SELECT
  distinct group_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(last_modified)               AS last_modified_max
FROM gav
GROUP BY group_id
ORDER BY group_id
;
select concat(now(), ' Table g refresh data finished');

-- Refresh data for table ga
TRUNCATE TABLE ga;
INSERT INTO    ga (
  group_id, artifact_id,
  artifact_version_counter,
  major_version_counter,
  version_seq_max,
  last_modified_max            )
SELECT 
  distinct group_id, artifact_id,
  count(DISTINCT artifact_version) AS artifact_version_counter,
  count(DISTINCT major_version)    AS major_version_counter,
  max(version_seq)                 AS version_seq_max,
  max(last_modified)               AS last_modified_max
FROM gav
GROUP BY group_id, artifact_id
ORDER BY group_id, artifact_id
;
select concat(now(), ' Table ga refresh data finished');
