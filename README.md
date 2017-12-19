# Status

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/pipeline-deploymon-plugin/master)](https://ci.jenkins.io/job/Plugins/job/pipeline-deploymon-plugin/job/master/)

# Features

This plugins adds Jenkins pipeline steps to interact with deploymon.io

[**see the changelog for release information**](#changelog)

# Usage / Steps

## notifyDeployment

Notify deploymon.io of the deployment of a new service version.

```
notifyDeployment(credentials: 'deploymon-TestProject', project:'someProjectId', service: 'serviceName', stage: 'stageName', version: '1.0')
```

# Changelog

## current master
* first release containing multiple pipeline steps
