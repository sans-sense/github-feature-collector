Github-Feature Collector
=================

Collector to get feature information from github.

Building and Deploying
--------------------------------------

Run
```
mvn package
```
to package the collector into an executable JAR file. Copy this file to your server and launch it using :
```
java -jar github-feature-collector-2.0.0-SNAPSHOT.jar --github-feature.cron="0 0/5 * * * *"
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


###Implementation hacks
--------------------------------------
Epics contain Sprints contain Features. While this is a good model for a professional project management tool, practical github issue usage for the same purpose is a bit .... So the collector gets all issues marked with label "story", stuffs them into a dummy sprint called default for a dummy team called default. The status and other dates for start and finish do work fine.

The api call is https://api.github.com/repos/[repoName]/issues?labels=story&state=all . Change the label in src/main/java/com/capitalone/dashboard/collector/DefaultGitHubFeatureClient.java. Time permitting, label would be configurable via GithubFeatureSettings.java which can be configured via github-feature.label  

We do not poll github much (api limit and fairness). Though the collector may run frequently, it does not update any project which it has updated in the last 30 minutes, github repo vs time is maintained in the option field of the collector item.