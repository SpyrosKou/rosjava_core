package org.ros.node;

/**
 * A rosjava logging facade used by {@link Node#getLog()}.
 *
 * <p>
 * {@link RosLog} intentionally exposes a small, stable subset of
 * {@link org.slf4j.Logger}. Implementations log through the project's
 * SLF4J-backed logger and also publish to {@link org.ros.Topics#ROSOUT} where
 * appropriate.
 *
 * <p>
 * The {@code fatal(...)} methods are provided because ROS logging conventions
 * commonly use that severity label even though SLF4J itself does not define a
 * dedicated fatal level.
 *
 * <p>
 * Typical application code should prefer this interface over binding directly
 * to a logging backend from node logic.
 *
 *
 * @author Spyros Koukas -
 */
public interface RosLog {
    String getName();

    boolean isDebugEnabled();

    boolean isInfoEnabled();

    boolean isWarnEnabled();
    boolean isErrorEnabled();

    boolean isFatalEnabled();

    void debug(String message);

    void debug(String message, Throwable throwable);

    void info(String message);

    void info(String message, Throwable throwable);

    void warn(String message);

    void warn(String message, Throwable throwable);

    void error(String message);

    void error(String message, Throwable throwable);

    void fatal(String message);

    void fatal(String message, Throwable throwable);




}
