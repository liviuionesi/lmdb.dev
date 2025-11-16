/**
 * Filmpire Shared Library Module
 * 
 * Provides common DTOs, exceptions, utilities, constants, and annotations
 * used across all Filmpire microservices.
 * 
 * This module can be used both as a Java module (requires com.filmpire.shared)
 * and as a regular classpath dependency (backward compatible).
 * 
 * Usage as Java module:
 *   module my.service {
 *       requires com.filmpire.shared;
 *   }
 * 
 * Usage as dependency (classpath):
 *   implementation project(':backend:shared-library')
 * 
 * @moduleGraph
 * @since 1.0.0
 */
module com.filmpire.shared {
    // Required modules
    requires java.base;
    
    // Spring Boot (automatic module names from JAR manifest)
    requires spring.boot;
    requires spring.boot.autoconfigure;
    
    // Jakarta Validation
    requires jakarta.validation;
    
    // Annotation processors (compile-time only, optional)
    requires static lombok;
    // MapStruct doesn't have a module - handled via annotation processing
    
    // Exported packages - public API
    exports com.filmpire.shared.dto;
    exports com.filmpire.shared.exception;
    exports com.filmpire.shared.util;
    exports com.filmpire.shared.constant;
    exports com.filmpire.shared.annotation;
}

