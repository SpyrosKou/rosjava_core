# rosjava_core

`rosjava_core` is a pure Java implementation of core ROS 1 client functionality.

This repository is the maintained fork at [SpyrosKou/rosjava_core](https://github.com/SpyrosKou/rosjava_core). The current fork targets desktop and server JRE usage, tracks newer Java and Gradle releases, and maintains a narrower public API for library use.

## Scope

- Targets the JRE on Windows, Linux, and macOS.
- Aims for a modern stack. Published artifacts are Java 17-compatible, while the build uses a Java 21 toolchain and Gradle 8.14.4.
- Keeps the core libraries and tutorial applications in one multi-project Gradle build.

## Improvements of [this fork](https://github.com/SpyrosKou/rosjava_core)

The [`rosjava`](./rosjava) module is the main focus of this fork. The highlights below are grouped by the kind of impact they have in practice.

### Reliability

- Correct service response ordering on persistent service connections. The service request/response path was updated so concurrent requests on a shared service connection are matched and completed in request order. This resolves the behavior reported in [rosjava/rosjava_core#261](https://github.com/rosjava/rosjava_core/issues/261).
- Explicitly covered service registration edge cases. Integration tests now capture the behavior discussed in [rosjava/rosjava_core#272](https://github.com/rosjava/rosjava_core/issues/272), including duplicate registration on the same node and the observable behavior when different nodes use the same service name.
- Fail-fast behavior for persistent service clients. Client-side shutdown, disconnects, write failures, and response-processing failures now complete waiting callbacks with a concrete error instead of leaving them blocked. For example, if two requests are queued and the connection closes, both waiting callbacks are completed with failure instead of leaving one request hanging.
- Ordered, non-overlapping listener callbacks. `ListenerGroup` was reworked so one listener sees callbacks in the order they were emitted and never handles two callbacks at once. For example, if events happen as `registered -> new subscriber -> shutdown`, that listener sees them in that order; if it receives messages `m1, m2, m3`, it processes `m1, m2, m3`, not `m2` before `m1`.

### Transport and Runtime Behavior

- Hardened TCP transport lifecycle and handshake flow. The TCP layer reuses shared Netty client infrastructure, releases transport and timer resources predictably on shutdown, registers subscribers only after the handshake reply has been written, and treats expected `ClosedChannelException` shutdown paths as normal close events. This avoids leftover transport threads, prevents premature subscriber activation, and keeps logs focused on real transport problems.
- Lower-overhead listener dispatch. Listener dispatch no longer keeps an idle thread per listener, which reduces background execution overhead while preserving ordered delivery for each listener.
- More predictable queue behavior under load. Queue handling was tightened by clearing overwritten `CircularBlockingDeque` entries and increasing the default `OutgoingMessageQueue` capacity, which reduces stale reference retention and handles short bursts more cleanly.

### Developer Experience

- Coordinated embedded `RosCore` lifecycle. `RosCore` exposes `awaitStart()` and `awaitShutdown(...)`, making application startup, teardown, and integration tests easier to coordinate.
- Current Java, Gradle, and logging baseline. Published artifacts target Java 17 compatibility, the build is exercised with JDK 21, the Gradle wrapper is 8.14.4, dependencies were refreshed, and logging uses SLF4J.

### API and Maintenance

- Safer Java-facing API surface. Service, publisher, and subscriber generics are constrained around ROS message types, internal visibility is reduced, and unnecessary inheritance points are closed.
- More explicit Java contracts. Return types were narrowed from generic `Collection` to `List` or `Set` where appropriate, internal result conversion was simplified around standard `Function<Object, T>`, and more classes were made deliberately `final`, making APIs and extension points easier to reason about.
- Leaner XML-RPC integration. The modified source copy in the repository has been replaced with published `org.apache.xmlrpc` artifacts, which reduces forked third-party code and makes the dependency story more standard for consumers.

## Usage Requirements

- Java 17+ for consuming the published artifacts
- JDK 21 available to Gradle for local builds
- Access to `mavenCentral()`
- Access to the ROS Java Maven repository at `https://github.com/SpyrosKou/rosjava_mvn_repo/raw/noetic`

## Use from another Gradle project

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
Practical usage notes for this repository are in [USAGE.md](./USAGE.md).
Maintainer publishing notes are in [PUBLISHING.md](./PUBLISHING.md).

## Tutorials

The tutorial modules remain useful as runnable examples. For example:

```powershell
.\gradlew.bat :rosjava_tutorial_pubsub:installDist
```

Each tutorial application uses `org.ros.RosRun` as its entry point.

## Build

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

## License

See [LICENSE](./LICENSE) and [LICENSING.md](./LICENSING.md).

## Changelog

See [CHANGELOG.rst](./CHANGELOG.rst).
