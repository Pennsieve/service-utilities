# service-utilities

A collection of utilities for use with Blackfynn web services.

## Publishing

### To your local repository for testing

Any branch:

```
$ sbt publishLocal
```

### A snapshot

Any branch (but usually should be `master`):

```
$ sbt publish
```
This will build the jar as a snapshot and push it to `maven-snapshots` on Nexus.

### A release

From your `master` branch:

```
$ sbt release
```

Follow the prompts to update the version and build the jar as an official release
and push it to `maven-releases` on Nexus.