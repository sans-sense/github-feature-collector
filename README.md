Github-Feature Collector
=================

Collector to get feature information from github.

Building and Deploying
--------------------------------------

Run
```
mvn install
```
to package the collector into an executable JAR file. Copy this file to your server and launch it using :
```
java -jar travis-ci-build-collector-2.0.0-SNAPSHOT.jar --github-feature.cron="0 0/5 * * * *"
```

###Database hacks
--------------------------------------
sSprintEndDate must be set, else the features are not even reported
db.feature.update({"sStatus" : "open"}, { '$set' : {"sSprintEndDate" : "2017-04-16T10:15:41.000+05:30"}}, {multi:true})  

sEpicId and sEstimate are needed or the code gives NPE and NFE  
db.feature.update({"sStatus" : "open"}, { '$set' : {"sEpicID" : "25"}}, {multi:true})  
db.feature.update({}, { '$set' : {"sEstimate" : "2"}}, {multi:true})  

Test Query  
db.feature.find({'sTeamID' : 'default' , 'isDeleted' : 'False', $and : [{'sSprintID' : {$ne : null}} , {'sSprintBeginDate' : {$lte : "2015-03-01T06:44:05.000Z"}} , {'sSprintEndDate' : {$gte : "2015-03-01T16:24:04.111Z"}}]})  
