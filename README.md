# [SpyrosKou/rosjava_core](https://github.com/SpyrosKou/rosjava_core)

A pure Java implementation of core ROS 1 client functionality that targets desktop and server JRE usage and newer Java and Gradle releases.
Provides a library-oriented public API and includes changes in reliability, transport behavior, and developer experience.



## Scope

- Targets the JRE on Windows, Linux, and macOS.
- Aims for a modern stack. Published artifacts are Java 17-compatible, while the build uses a Java 21 toolchain and Gradle 8.14.4.
- Keeps the core libraries and tutorial applications in one multi-project Gradle build.

## Highlights

- Persistent service-client behavior was tightened: shared connections now preserve response ordering, and pending calls fail explicitly on shutdown or transport failure.
- Listener dispatch now preserves per-listener order without overlapping callbacks and avoids idle thread-per-listener execution.
- Transport lifecycle handling is more predictable: subscriber handshake activation is delayed correctly, Netty resources are reused and released cleanly, and expected shutdown noise is reduced.
- Embedded `RosCore` startup and shutdown are easier to coordinate through `awaitStart()` and `awaitShutdown(...)`.
- The public Java API was narrowed and clarified with stricter ROS message generics, reduced visibility, more deliberate `final` usage, and more specific collection return types.
- The build and publishing baseline was modernized around Java 17-compatible artifacts, a Java 21 toolchain, Gradle 8.14.4, refreshed dependencies, and `maven-publish`.


## Usage

### Usage Notes
Practical usage notes for this repository are in [USAGE.md](./USAGE.md).


### Usage Requirements

- Java 17+ for consuming the published artifacts
- JDK 21 available to Gradle for local builds
- Access to `mavenCentral()`
- Access to the ROS Java Maven repository at `https://github.com/SpyrosKou/rosjava_mvn_repo/raw/noetic`

### Use from another Gradle project

Published artifacts for this fork are available from the ROS Java Maven repository:

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri("https://github.com/SpyrosKou/rosjava_mvn_repo/raw/noetic")
    }
}

dependencies {
    implementation("org.ros.rosjava_core:rosjava:0.4.1.1")
    implementation("org.ros.rosjava_core:rosjava_geometry:0.4.1.1")
    implementation("org.ros.rosjava_core:rosjava_helpers:0.4.1.1")
}
```

Use the modules you actually need. `rosjava` is the core dependency; `rosjava_geometry` and `rosjava_helpers` are optional add-ons.
For a complete end-to-end usage example, see [Plain-ROS-Java-System-Example](https://github.com/SpyrosKou/Plain-ROS-Java-System-Example).

### Tutorials

The tutorial modules remain useful as runnable examples. For example:

```powershell
.\gradlew.bat :rosjava_tutorial_pubsub:installDist
```

Each tutorial application uses `org.ros.RosRun` as its entry point.

### Build

Run explicit Gradle tasks from the repository root.

On Windows:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

On Linux or macOS:

```bash
./gradlew compileJava
./gradlew test
```

Do not rely on invoking the wrapper with no task name. Several modules define `publish` and `installDist` as default tasks, so an unqualified `gradlew` run is not the safest entry point for normal development.


## Modules

See [MODULES.md](./MODULES.md).

## Publishing
Maintainer publishing notes are in [PUBLISHING.md](./PUBLISHING.md).

## License

See [LICENSE](./LICENSE) and [LICENSING.md](./LICENSING.md).

## Changelog

See [CHANGELOG.rst](./CHANGELOG.rst).

