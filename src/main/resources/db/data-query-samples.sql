-- Groups/Artifacts Most Revised Recently

SELECT 
    json->>"$.groupId"  as group_id,
    count(*)            as counter
  FROM mvnrepos.artifactinfo
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
  FROM mvnrepos.artifactinfo
  WHERE classifier is null
    and json->>"$.fileExtension" in (SELECT extension FROM binarydocjvmadm.extension)
    and last_modified > DATE_SUB(NOW(),INTERVAL 1 YEAR) 
  GROUP BY json->>"$.groupId", json->>"$.artifactId"
  ORDER BY counter DESC
;


-- Artifact Counter by Country

SELECT group_id_left1, count(*) as counter
  FROM mvnrepos.ga
  WHERE length(group_id_left1) = 2
  group by group_id_left1
  order by counter desc
;

-- Artifact Counter by Company / Orgniazation

SELECT group_id_left2, count(*) as counter
FROM mvnrepos.ga
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

