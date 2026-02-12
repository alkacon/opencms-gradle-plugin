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

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Provides a DSL for configuration of OpenCms default dependencies.
 */
public class OpenCmsModulesExtension {

    /** Default configuration. */
    public static final String DEFAULT_JUNIT_VERSION = "4.12"
    
    /** Default configuration. */
    public static final String DEFAULT_HSQLDB_VERSION = "2.3.2"

    /**
     * Internal class with all configuration details.
     */
    static class DependencyConfiguration {

        /** Flag to activate dependency. */
        final Property<Boolean> opencmsCore;
        
        /** Flag to activate dependency. */
        final Property<Boolean> opencmsSetup;
        
        /** Flag to activate dependency. */
        final Property<Boolean> opencmsGwt;
        
        /** Flag to activate dependency. */
        final Property<Boolean> opencmsModules;
        
        /** Flag to activate dependency. */
        final Property<Boolean> opencmsTest;
        
        /** Dependency configuration. */
        final Property<String> hsqldb;
        
        /** Dependency configuration. */
        final Property<String> junit;

        /**
         * Creates a new dependency configuration.
         * @param objects
         * @param core
         * @param gwt
         * @param modules
         * @param setup
         * @param test
         * @param hsqldb
         * @param junit
         */
        @Inject
        public DependencyConfiguration(ObjectFactory objects, boolean core, boolean gwt, boolean modules, boolean setup, boolean test, String hsqldb, String junit) {

            this.opencmsCore = objects.property(Boolean).convention(core)
            this.opencmsGwt = objects.property(Boolean).convention(gwt)
            this.opencmsModules = objects.property(Boolean).convention(modules)
            this.opencmsSetup = objects.property(Boolean).convention(setup)
            this.opencmsTest = objects.property(Boolean).convention(test)
            this.hsqldb = objects.property(String).convention(hsqldb)
            this.junit = objects.property(String).convention(junit)
        }

        /**
         * Returns the list of configured dependencies for a given OpenCms version.
         * @param opencmsVersion the OpenCms version
         * @return the list of
         */
        public List<String> getDependencies(String opencmsVersion) {
 
            List<String> dependencies = [];
            if (opencmsCore.get()) {
                dependencies.add("org.opencms:opencms-core:${opencmsVersion}")
            }
            if (opencmsGwt.get()) {
                dependencies.add("org.opencms:opencms-gwt:${opencmsVersion}")
            }
            if (opencmsModules.get()) {
                dependencies.add("org.opencms:opencms-modules:${opencmsVersion}")
            }
            if (opencmsSetup.get()) {
                dependencies.add("org.opencms:opencms-setup:${opencmsVersion}")
            }
            if (opencmsTest.get()) {
                dependencies.add("org.opencms:opencms-test:${opencmsVersion}")
            }
            if (!hsqldb.get().isEmpty()) {
                dependencies.add("org.hsqldb:hsqldb:${hsqldb.get()}")
            }
            if (!junit.get().isEmpty()) {
                dependencies.add("junit:junit:${junit.get()}")
            }
            return dependencies;
        }

        /**
         * Prints the actual configuration.
         * @param linePrefix
         */
        public void printState(String linePrefix) {

            println (linePrefix + "opencmsCore=" + opencmsCore.get())
            println (linePrefix + "opencmsGwt=" + opencmsGwt.get())
            println (linePrefix + "opencmsModules=" + opencmsModules.get())
            println (linePrefix + "opencmsSetup=" + opencmsSetup.get())
            println (linePrefix + "opencmsTest=" + opencmsTest.get())
            println (linePrefix + "hsqldb=" + hsqldb.get())
            println (linePrefix + "junit=" + junit.get())
        }
    }

    /** Configuration details for compile { } */
    final DependencyConfiguration compile
    
    /** Configuration details for testCompile { } */
    final DependencyConfiguration testCompile

    /** Whether there are module specific dependencies. */
    final Property<Boolean> inModuleDeps

    /** Whether to ignore the test dependencies. */
    final Property<Boolean> ignoreTestDeps

    /**
     * Creates a new configuration.
     * @param objects
     */
    @Inject
    public OpenCmsModulesExtension(ObjectFactory objects) {

        this.inModuleDeps = objects.property(Boolean).convention(false)
        this.ignoreTestDeps = objects.property(Boolean).convention(false)
        this.compile = objects.newInstance(DependencyConfiguration, true, false, true, false, false, "", "")
        this.testCompile = objects.newInstance(DependencyConfiguration, false, false, false, true, true, DEFAULT_HSQLDB_VERSION, DEFAULT_JUNIT_VERSION)
    }

    /**
     * DSL method for compile { }
     * @param action
     */
    void compile(Action<? super DependencyConfiguration> action) {

        action.execute(compile)
    }

    /**
     * DSL method for testCompile { }
     * @param action
     */
    void testCompile(Action<? super DependencyConfiguration> action) {
        
        action.execute(testCompile)
    }

    /**
     * Prints the actual dependency configuration.
     */
    public void printState() {
        println ""
        println "OpenCms default dependencies are configured as follows"
        println "======================================================"
        println "ocDefaultDependencies {"
        println "  compile {"
        compile.printState("    ")
        println "  }"
        println "  testCompile {"
        testCompile.printState("    ")
        println "  }"
        println "  inModuleDeps=${inModuleDeps.get()}"
        println "  ignoreTestDeps=${ignoreTestDeps.get()}"
        println "}"
        println ""
    }
}
