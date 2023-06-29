package org.ros.node;

import org.slf4j.Marker;

/**
 * A subset of {@link org.slf4j.Logger} that allows logging in a {@link org.slf4j.Logger} and published in {@link org.ros.Topics#ROSOUT}
 * Created at 2022-06-15 on 12:43
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
