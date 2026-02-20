package org.opencms

import org.gradle.api.Project

/**
 * Provides a DSL delegate for configuring module-specific dependencies using the {@code ocModule} notation.
 * 
 * <p><strong>Usage example in 'dependencies.gradle':</strong></p>
 * <pre>
 * ocModule('org.opencms.module') {
 *     moduleDeps 'org.apache.commons:commons-lang3:3.12.0'
 *     moduleDeps project(':shared-utils')
 * }
 * </pre>
 */
class OpenCmsModuleDependency {

    /** The Gradle project instance. */
    private final Project project

    /** The unique name of the OpenCms module (e.g., 'org.opencms.module'). */
    private final String moduleName

    /**
     * Creates a new dependency DSL delegate for a specific module.
     * @param project the Gradle project to which dependencies will be added
     * @param moduleName the identifier of the module as defined in the 'modules_list'
     */
    OpenCmsModuleDependency(Project project, String moduleName) {

        this.project = project
        this.moduleName = moduleName
    }

    /**
     * Catch-all for all other configurations (implementation, testImplementation, etc.).
     * This redirects calls to the project's standard dependency handler.
     */
    def methodMissing(String name, Object args) {

        if (args && args.length > 0) {
            return project.dependencies.add(name, args[0])
        }
        throw new MissingMethodException(name, this.class, args)
    }

    /**
     * Adds a dependency to the module-specific configuration {@code moduleDeps_${moduleName}}.
     * @param dependencyNotation the dependency notation
     */
    void moduleDeps(Object dependencyNotation) {

        this.project.dependencies.add("moduleDeps_${moduleName}", dependencyNotation)
    }
}