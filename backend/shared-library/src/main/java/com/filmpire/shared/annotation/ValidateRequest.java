package com.filmpire.shared.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable automatic validation of request parameters.
 * When applied to a controller method, all request parameters annotated with
 * {@code @Valid} or {@code @Validated} will be automatically validated.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @ValidateRequest
 * @PostMapping("/users")
 * public ResponseEntity createUser(@Valid @RequestBody UserDTO userDTO) {
 *     // method implementation
 * }
 * }
 * </pre>
 *
 * @author Filmpire Development Team
 * @version 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateRequest {

    /**
     * Groups to validate (for validation groups)
     *
     * @return validation groups
     */
    Class<?>[] groups() default {};

    /**
     * Whether to fail fast (stop on first validation error)
     *
     * @return true to fail fast
     */
    boolean failFast() default false;
}



