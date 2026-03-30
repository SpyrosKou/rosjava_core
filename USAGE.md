# Usage

Practical usage notes for the current `rosjava_core` fork.

## Programming Model

`rosjava` applications are usually written as `NodeMain` implementations.
`NodeMain` supplies the node name and participates in the node lifecycle through
the inherited `NodeListener` callbacks.

The usual pattern is:

1. Implement `getDefaultNodeName()`.
2. Create publishers, subscribers, services, and parameter access in `onStart(ConnectedNode)`.
3. Use `ConnectedNode` and `Node` helper APIs instead of treating the code as a direct thread or socket API.

Unlike many ROS client libraries, multiple rosjava nodes can run inside one JVM.
Long-running work should not block lifecycle callbacks directly. For repeated
background work, prefer `Node.executeCancellableLoop(...)` so the loop is
stopped when the node shuts down.

ROS time can be wall-clock or simulated. When your code needs ROS time, prefer
`ConnectedNode.getCurrentTime()` instead of Java wall-clock APIs.

## Topics

`ConnectedNode` is the main factory for topic APIs:

- `newPublisher(...)` creates a `Publisher<T>`
- `newSubscriber(...)` creates a `Subscriber<T>`

See the runnable tutorial sources for minimal examples:

- [`Talker.java`](./rosjava_tutorial_pubsub/src/main/java/org/ros/rosjava_tutorial_pubsub/Talker.java)
- [`Listener.java`](./rosjava_tutorial_pubsub/src/main/java/org/ros/rosjava_tutorial_pubsub/Listener.java)

To build a tutorial distribution on Windows:

```powershell
.\gradlew.bat :rosjava_tutorial_pubsub:installDist
```

The installed launcher uses `org.ros.RosRun` as the application entry point.

## Services

`ConnectedNode.newServiceServer(...)` creates a service server and
`ConnectedNode.newServiceClient(...)` creates a service client.

See the runnable tutorial sources for service examples:

- [`ServiceServerNode.java`](./rosjava_tutorial_services/src/main/java/org/ros/rosjava_tutorial_services/ServiceServerNode.java)
- [`Client.java`](./rosjava_tutorial_services/src/main/java/org/ros/rosjava_tutorial_services/Client.java)

In this fork, `ServiceClient` instances use persistent service connections.
That means repeated calls can reuse the same transport connection instead of
reconnecting for every request. Shutdown and transport-failure behavior for
pending calls is documented in the current API Javadocs.

## Parameters

Use `ConnectedNode.getParameterTree()` to access the ROS parameter server.

`ParameterTree` is intentionally typed. Callers are expected to know which kind
of value they are retrieving:

- booleans
- integers
- doubles
- strings
- lists
- maps

If the stored value does not match the requested type, rosjava throws a
`ParameterClassCastException`. If you retrieve a subtree or collection value,
you are responsible for casting nested values to the types your application
expects.

`ParameterListener` subscriptions apply to a specific parameter key, not to an
entire subtree.

## Logging

Use `Node.getLog()` to obtain a `RosLog`.

`RosLog` is a rosjava logging facade backed by the project's SLF4J-based
logging stack and mirrored to `/rosout` where applicable.

## Advanced Notes

### Listener Callbacks

Publisher, subscriber, service, and node APIs expose listener hooks for
lifecycle events. These listeners are useful when your code needs to react to
registration, shutdown, or peer-connection events instead of only handling
messages or service responses.

For example, a `PublisherListener` can detect registration failures or new
subscribers. Current listener dispatch preserves per-listener callback order and
does not invoke the same listener concurrently.

### Raw Message Deserialization

If you deserialize ROS wire-format message payloads manually, remember that ROS
message data is little-endian. When wrapping raw bytes in a `ByteBuffer`, set
the buffer order to `ByteOrder.LITTLE_ENDIAN` before passing it to a message
deserializer.

### Transport Hints

Subscriber creation can optionally take `TransportHints`.

The current public hint is `tcpNoDelay`, which requests low-latency TCP
behavior for subscribers when the remote publisher and transport path support
it.
