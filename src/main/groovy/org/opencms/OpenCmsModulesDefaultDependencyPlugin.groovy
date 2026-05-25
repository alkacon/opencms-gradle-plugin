/*
 * File   : $Source$
 * Date   : $Date$
 * Version: $Revision$
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2008 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms;

import org.gradle.api.artifacts.Configuration
import org.gradle.api.Plugin
import org.gradle.api.Project

public class OpenCmsModulesDefaultDependencyPlugin implements Plugin<Project> {

    /**
     * Called when applying to the build project.<p>
     *
     * @param project the project that is being build
     **/
    @Override
    void apply(Project project) {

        def extension = project.getExtensions().create('ocDependencies', OpenCmsModulesExtension)
        project.logger.lifecycle("Initialized ocDependencies.")
        project.tasks.register('ocDependenciesDemo') {
            group = "OpenCms"
            description = "Displays a ocDependencies { } configuration example."
            doLast {
                extension.printState(logger)
            }
        }
        project.plugins.withId('java') {
            project.configurations.named('implementation').configure { Configuration config ->
                config.withDependencies { dependencies ->  // lazily evaluated when java dependencies are available
                    String opencmsVersion = project.findProperty('opencms_version') ?: 'master'
                    extension.compile.getDependencies(opencmsVersion).each { notation ->
                        dependencies.add(project.dependencies.create(notation))
                    }
                }
            }
            project.configurations.named('testImplementation').configure { Configuration config ->
                config.withDependencies { dependencies -> // lazily evaluated when java dependencies are available
                    String opencmsVersion = project.findProperty('opencms_version') ?: 'master'
                    if (!extension.ignoreTestDeps.get()) {
                        extension.testCompile.getDependencies(opencmsVersion).each { notation ->
                            dependencies.add(project.dependencies.create(notation))
                        }
                    }
                }
            }
        }
    }
}
