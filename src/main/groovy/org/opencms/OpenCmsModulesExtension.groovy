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


public class OpenCmsModulesExtension {

    public static final String DEFAULT_JUNIT_VERSION = "4.12"
    public static final String DEFAULT_HSQLDB_VERSION = "2.3.2"
    public class DependencyConfiguration {

        boolean opencmsCore;
        boolean opencmsSetup;
        boolean opencmsGwt;
        boolean opencmsModules;
        boolean opencmsTest;
        boolean hsqldb;
        boolean junit;

        public DependencyConfiguration(boolean core, boolean gwt, boolean modules, boolean setup, boolean test, String hsqldb, String junit) {
            opencmsCore = core
            opencmsGwt = gwt
            opencmsModules = modules
            opencmsSetup = setup
            opencmsTest = test
            this.hsqldb = hsqldb
            this.junit = junit
        }

        public List<String> getDependenies(String opencmsVersion) {
            List<String> dependencies = new ArrayList<>(7);
            if (opencmsCore) {
                dependencies.add(makeDependency('org.opencms', 'opencms-core', opencmsVersion))
            }
            if (opencmsGwt) {
                dependencies.add(makeDependency('org.opencms', 'opencms-gwt', opencmsVersion))
            }
            if (opencmsModules) {
                dependencies.add(makeDependency('org.opencms', 'opencms-modules', opencmsVersion))
            }
            if (opencmsSetup) {
                dependencies.add(makeDependency('org.opencms', 'opencms-setup', opencmsVersion))
            }
            if (opencmsTest) {
                dependencies.add(makeDependency('org.opencms', 'opencms-test', opencmsVersion))
            }
            if (hsqldb) {
                dependencies.add(makeDependency('org.hsqldb', 'hsqldb', DEFAULT_HSQLDB_VERSION))
            }
            if (junit) {
                dependencies.add(makeDependency('junit', 'junit', DEFAULT_JUNIT_VERSION))
            }
            return dependencies;
        }

        private String makeDependency(String group, String artifact, String version) {
            return "${group}:${artifact}:${version}"
        }
        public void printState(String linePrefix) {
            println (linePrefix + "opencmsCore=" + opencmsCore)
            println (linePrefix + "opencmsGwt=" + opencmsGwt)
            println (linePrefix + "opencmsModules=" + opencmsModules)
            println (linePrefix + "opencmsSetup=" + opencmsSetup)
            println (linePrefix + "opencmsTest=" + opencmsTest)
            println (linePrefix + "hsqldb=" + hsqldb)
            println (linePrefix + "junit=" + junit)
        }
    }
    DependencyConfiguration compile = new DependencyConfiguration(true,false,true,false,false,"","");
    DependencyConfiguration testCompile = new DependencyConfiguration(false,false,false,true,true,DEFAULT_HSQLDB_VERSION,DEFAULT_JUNIT_VERSION);
    boolean inModuleDeps = false
    boolean ignoreTestDeps = false

    void compile(Closure closure) {
        closure.delegate = compile
        closure()
    }

    void testCompile(Closure closure) {
        closure.delegate = testCompile
        closure()
    }

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
        println "  inModuleDeps=${inModuleDeps}"
        println "  ignoreTestDeps=${ignoreTestDeps}"
        println "}"
        println ""
    }
}
