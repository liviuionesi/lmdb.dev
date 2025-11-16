package com.filmpire.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to log method execution time.
 * Can be used on methods to automatically log how long they take to execute.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @LogExecutionTime
 * public void someMethod() {
 *     // method implementation
 * }
 * }
 * </pre>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {

    /**
     * Optional custom message prefix for the log
     *
     * @return custom message prefix
     */
    String value() default "";

    /**
     * Log level for the execution time log
     *
     * @return log level (INFO, DEBUG, WARN)
     */
    LogLevel level() default LogLevel.INFO;

    /**
     * Threshold in milliseconds - only log if execution time exceeds this value
     * Default is 0 (log all executions)
     *
     * @return threshold in milliseconds
     */
    long thresholdMillis() default 0;

    /**
     * Enum representing log levels
     */
    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}



