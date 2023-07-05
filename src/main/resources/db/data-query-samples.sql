-- Groups/Artifacts Most Revised Recently

SELECT 
    json->>"$.groupId"  as group_id,
    count(*)            as counter
  FROM mavendb.artifactinfo
  WHERE classifier is null
    and json->>"$.fileExtension" in (SELECT extension FROM binarydocjvmadm.extension)
    and last_modified > DATE_SUB(NOW(),INTERVAL 1 YEAR) 
  GROUP BY json->>"$.groupId"
  ORDER BY counter DESC
;

SELECT 
    json->>"$.groupId"     as group_id,
    json->>"$.artifactId"  as artifact_id,
    count(*)               as counter
  FROM mavendb.artifactinfo
  WHERE classifier is null
    and json->>"$.fileExtension" in (SELECT extension FROM binarydocjvmadm.extension)
    and last_modified > DATE_SUB(NOW(),INTERVAL 1 YEAR) 
  GROUP BY json->>"$.groupId", json->>"$.artifactId"
  ORDER BY counter DESC
;


-- Artifact Counter by Country

SELECT group_id_left1, count(*) as counter
  FROM mavendb.ga
  WHERE length(group_id_left1) = 2
  group by group_id_left1
  order by counter desc
;

-- Artifact Counter by Company / Orgniazation

SELECT group_id_left2, count(*) as counter
FROM mavendb.ga
group by group_id_left2
order by counter desc
;


-- Level 1 Group

SELECT
  group_id_left1,
  count(*) as counter
FROM ga
GROUP BY group_id_left1
ORDER BY counter desc, group_id_left1
;

-- Get keys in the maven artifact
SELECT json_keys(json)
FROM `artifactinfo`
LIMIT 100
;

-- All Keys defined in artifactinfo
-- SEE : https://dev.mysql.com/doc/refman/8.0/en/json-table-functions.html#function_json-table
-- WARN: This query will be very slow due to the data set size on maven central
--
-- 18 Rows found as of June 18, 2023
/*
"artifactId"
"attributes"
"classifier"
"context"
"description"
"fileExtension"
"groupId"
"javadocExists"
"lastModified"
"matchHighlights"
"name"
"packaging"
"repository"
"sha1"
"signatureExists"
"size"
"sourcesExists"
"version"
*/

SELECT distinct json_key FROM  artifactinfo,
  json_table(
    json_keys(json),
    '$[*]' COLUMNS(json_key JSON PATH '$')
  ) t
ORDER BY json_key
;



SELECT jt.* FROM artifactinfo,
json_table(
  json,
  '$[*]' COLUMNS(
    rowid FOR ORDINALITY,
    groupId   JSON PATH "$.groupId"
  )
) jt
limit 100
;

-- Believe or not, the max length of sha1 in maven central is 92, as of Jun 18, 2023
-- These wrong data is from two artifacts
--  "groupId": "org.apache.spark", "version": "1.2.0"
--  "groupId": "org.apache.axis2", "version": "1.0.0", "packaging": "jar", "artifactId": "axis2-transport-http-tests",
SELECT max(length(json->>"$.sha1"))
FROM `artifactinfo`
;

SELECT *, HEX(`uinfo_md5`) AS `uinfo_md5`
FROM `artifactinfo`
WHERE length(json->>"$.sha1") > 40
;


-- Export json
SELECT json
FROM `artifactinfo`
INTO outfile '/var/lib/mysql-files/artifactinfo.data'
;

