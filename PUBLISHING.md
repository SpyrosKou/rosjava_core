# Publishing

Maintainer-facing publishing notes for this fork.

## Published modules

- [`rosjava`](./rosjava)
- [`rosjava_geometry`](./rosjava_geometry)
- [`rosjava_helpers`](./rosjava_helpers)

## Publish to the local Maven repo

On Windows:

```powershell
.\gradlew.bat publishToMavenLocal
```

On Linux or macOS:

```bash
./gradlew publishToMavenLocal
```

## Publish to a file-based Maven repository

`ROS_MAVEN_DEPLOYMENT_REPOSITORY` is a Gradle project property.
You can pass it per invocation with `-P`, put it in `gradle.properties` in the project root or `GRADLE_USER_HOME`, or expose it as the environment variable `ORG_GRADLE_PROJECT_ROS_MAVEN_DEPLOYMENT_REPOSITORY`.
See Gradle's [Build Environment Configuration](https://docs.gradle.org/current/userguide/build_environment.html#sec:project_properties).

On Windows:

```powershell
.\gradlew.bat publish -PROS_MAVEN_DEPLOYMENT_REPOSITORY=D:\path\to\maven-repo
```

On Linux or macOS:

```bash
./gradlew publish -PROS_MAVEN_DEPLOYMENT_REPOSITORY=/path/to/maven-repo
```
