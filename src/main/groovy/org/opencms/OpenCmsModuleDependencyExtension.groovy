package org.opencms

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Entry point for the {@code ocModule} DSL extension.
 */
class OpenCmsModuleDependencyExtension {

    /** The Gradle project this extension is applied to. */
    private final Project project

    /**
     * Creates a new extension.
     * @param project the project instance used for configuration and validation
     */
    OpenCmsModuleDependencyExtension(Project project) {
        this.project = project
    }

    /**
     * Configures dependencies for a specific OpenCms module.
     * @param name the name of the module to configure (must be in {@code modules_list})
     * @param configure the configuration closure containing {@code moduleDeps} calls
     * @throws GradleException if the module name is not declared in the project's {@code modules_list}
     */
    void call(String name, Closure configure) {

        if (!project.ext.allModuleNames.contains(name)) {
            throw new GradleException("Module '${name}' is not declared in 'modules_list'!")
        }
        def delegate = new OpenCmsModuleDependency(project, name)
        configure.delegate = delegate
        configure.resolveStrategy = Closure.DELEGATE_FIRST
        configure.call()
    }
}
