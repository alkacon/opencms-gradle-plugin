
package org.opencms

import org.apache.tools.ant.filters.ReplaceTokens

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper

/**
 * This Gradle plugin should be used to build modules for OpenCms.<p>
 *
 * It assumes each module is placed in a folder named as the module within the project folder.<p>
 * This module folder should contain a 'resources' folder containing all module VFS resource
 * including the module manifest.xml file.<p>
 * Optionally the module folder may contain a 'src' folder containing the java source file
 * required by the module.<p>
 * Optionally it may contain a 'static' folder containing static resources that will be placed
 * into the module JAR file and will be accessible through static resource URLs within OpenCms.<p>
 *
 * All module names of the project must be listed in the build property 'modules_list' as comma
 * separated values.<p>
 * Also the artifact name needs be provided with the build property 'project_name' as well as the
 * project nice name with 'project_nice_name' and the build version with 'build_version'.<p>
 *
 * Use the task 'bindist' to build all project modules and 'install' to make project artifacts
 * available within your local maven cache.<p>
 **/
class OpenCmsModulesPlugin implements Plugin<Project> {

    private static final String GRADLE_VERSION='6.1'
    private static final String DEFAULT_JAVA_COMPATIBILITY='1.8'
    private static final String PROJECT_EXTENSION = "ocDependencies"

    private static final String HORIZONTAL_LINE='==============================================================================='

    /**
     * Called when applying to the build p.<p>
     *
     * @param project the project that is being build
     **/
    void apply(Project p) {

        def ocDependencies = null;
        try {
            ocDependencies = p.getExtensions().getByName('ocDependencies');
        } catch (UnknownDomainObjectException ex) {
            // this is ok.
        }

        if (p.hasProperty('build_directory')) {
            p.buildDir = p.build_directory
        }

        if (p.hasProperty('java_target_version')) {
            p.targetCompatibility = p.java_target_version
        } else {
            println "Using plugins default Java target compatibility (${DEFAULT_JAVA_COMPATIBILITY}). To overwrite use property 'java_target_version'."
            p.targetCompatibility = DEFAULT_JAVA_COMPATIBILITY
        }
        if (p.hasProperty('java_source_version')) {
            p.sourceCompatibility = p.java_source_version
        } else {
            println "Java source compatibility not explicitly specified. Using target compatiblity (${p.targetCompatibility}). To overwrite use property 'java_source_version'."
            p.sourceCompatibility = p.targetCompatibility
        }

        p.ext.gwtStyle='obfuscated'
        if (p.hasProperty('gwt_style')) {
            p.gwtStyle=p.gwt_style
        }

        p.ext.gwtMode='strict'
        if (p.hasProperty('gwt_mode')) {
            p.gwtMode=p.gwt_mode
        }


        p.repositories {
            mavenLocal()
            if (p.hasProperty('additional_repositories')){
                p.additional_repositories.split(';').each{ repo ->
                    maven {
                        url repo
                    }
                }
            }
            jcenter()
            maven {
                url "http://maven.nuiton.org/release/"
            }
            maven {
                url "http://software.rescarta.org/nexus/content/repositories/thirdparty/"
            }
            maven {
                url "http://maven.vaadin.com/vaadin-addons"
            }
        }

        p.configurations {
            moduleDeps {
                description = 'additional dependencies required by modules, need to be added into the webapp lib folder'
            }
            compile {
                description = 'used to compile the modules jars'
                extendsFrom moduleDeps
            }
            testCompile {
                description = 'used to compile and execute test cases'
                extendsFrom compile
            }
        }
        if (null != ocDependencies) {
            println "You are using default OpenCms dependencies with the following configuration:"
            println ""
            ocDependencies.printState()
            if (ocDependencies.addDefaultOpenCmsDependencies) {
                println "Adding default opencms compile dependencies:"
                println " - compile group: 'org.opencms', name: 'opencms-core', version: ${p.opencms_version}"
                println " - moduleDeps p.fileTree(dir: '.').matching {"
                println "     include '**/lib/*.jar'"
                println "   }"
                p.dependencies {
                    compile group: 'org.opencms', name: 'opencms-core', version: p.opencms_version
                    moduleDeps p.fileTree(dir: '.').matching {
                        include '**/lib/*.jar'
                    }
                }
            }
            if (ocDependencies.addDefaultOpenCmsTestDependencies) {
                println "Adding default opencms testCompile dependencies:"
                println " - compile group: 'org.opencms', name: 'opencms-setup', version: ${p.opencms_version}"
                println " - compile group: 'org.opencms', name: 'opencms-modules', version: ${p.opencms_version}"
                println " - compile group: 'org.opencms', name: 'opencms-test', version: ${p.opencms_version}"
                p.dependencies {
                    compile group: 'org.opencms', name: 'opencms-setup', version: p.opencms_version
                    compile group: 'org.opencms', name: 'opencms-modules', version: p.opencms_version
                    compile group: 'org.opencms', name: 'opencms-test', version: p.opencms_version
                }
            }
            def jUnitDep = ocDependencies.addJUnitTestDependencyVersion;
            if (null != jUnitDep && !jUnitDep.isEmpty()) {
                println "Adding junit version ${jUnitDep} as testCompile dependency:"
                println " - testCompile group: 'junit', name: 'junit', version: ${jUnitDep}"

                p.dependencies {
                    testCompile group: 'junit', name: 'junit', version: jUnitDep
                }
            }
            def hSqlDbDep = ocDependencies.addHSqlDbDependencyVersion;
            if (null != hSqlDbDep && !hSqlDbDep.isEmpty()) {
                println "Adding hsqldb version ${hSqlDbDep} as testCompile dependency."
                println " - testCompile group: 'junit', name: 'junit', version: ${hSqlDbDep}"
                p.dependencies {
                    testCompile group: 'org.hsqldb', name: 'hsqldb', version: hSqlDbDep
                }
            }
        }

        if (p.file("dependencies.gradle").exists()) {
            println "Adding custom dependencies from 'dependencies.gradle'"
            p.apply([from: 'dependencies.gradle'])
        } else {
            println "No custom dependencies loaded. Add file 'dependencies.gradle' with to dependencies to add some."
        }

        if (p.ext.has('coreproject')) {
            def coreproject = p.ext.coreproject
            println "The Modules are compiled with the core directly."
            println "Replacing dependencies to the following core artifacts with the core project \"${coreproject}\":"
            println " - org.opencms:opencms-core"
            println " - org.opencms:opencms-modules"
            println " - org.opencms:opencms-test"
            println " - org.opencms:opencms-setup"
            p.configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute module("org.opencms:opencms-core") with project(coreproject)
                    substitute module("org.opencms:opencms-modules") with project(coreproject)
                    substitute module("org.opencms:opencms-test") with project(coreproject)
                    substitute module("org.opencms:opencms-setup") with project(coreproject)
                }
            }
        }

        p.sourceSets {
            test {
                java {
                    srcDir "test/src"
                }
                compileClasspath=p.configurations.testCompile
            }
        }

        p.task('opencmsPluginDescription'){
            doFirst{
                println HORIZONTAL_LINE
                println 'OpenCms modules plugin description'
                println ''
                println 'Use this plugin to build modules for OpenCms.'
                println ''
                println 'Required build properties:'
                println '    build_version: the artifact version'
                println '    project_name: the project name used to name artifacts'
                println '    project_nice_name: the nice name of the project used in artifact description'
                println '    modules_list: the comma separated list of module names'
                println ''
                println 'Optional build properties:'
                println '    build_directory: the build directory'
                println '    java_target_version: the java source and compatibility version'
                println '    max_heap_size: the heap size to use during GWT build'
                println '    gwt_style: the GWT output style, use pretty for debug build, defaults to obfuscated'
                println '    gwt_mode: the GWT compile mode, use draftCompile to speed up build times, defaults to strict'
                println HORIZONTAL_LINE
            }
        }

        try {
            p.ext.modulesDistsDir = p.file("${p.buildDir}/modulesZip")
            p.ext.moduleLibs=''
            p.ext.dependencyLibs=''
            p.ext.modulesAll=''

            if (!p.hasProperty('max_heap_size')){
                p.ext.max_heap_size='1024m'
            }

            p.ext.allModuleNames = p.modules_list.split(',')
            p.ext.modulesDistsDir = p.file("${p.buildDir}/modulesZip")

            // iterate all configured modules
            p.allModuleNames.each{ moduleName ->
                p.sourceSets.create(moduleName,{
                    java {
                        srcDir "${moduleName}/src"
                        exclude '**/test/**'
                    }
                    resources.srcDir "${moduleName}/src"
                });
                p.sourceSets[moduleName].compileClasspath=p.configurations.compile
                def moduleFolder = p.file("${moduleName}")
                def srcModule = p.file("${moduleFolder}/src")
                def srcGwtDir = p.file("${moduleFolder}/src-gwt")
                def staticFolder=p.file("${moduleFolder}/static")
                def requiresJar = srcModule.exists() || srcGwtDir.exists() || staticFolder.exists()
                def manifestFile = p.file("${moduleFolder}/resources/manifest.xml")
                def gwtModule = null
                def gwtSourceSetName = null
                def propertyFile = p.file("${moduleFolder}/module.properties")
                def gwtRename = null
                if (propertyFile.exists()){
                    p.logger.lifecycle("checking properties for module $moduleName")
                    Properties moduleProperties= new Properties()
                    moduleProperties.load(new FileInputStream(propertyFile))
                    if (moduleProperties['module.gwt']!=null){
                        gwtModule = moduleProperties['module.gwt']
                        gwtSourceSetName = moduleName.replace(".", "_")+'_gwt'
                        p.logger.lifecycle("found GWT module $gwtModule")
                        def moduleXml = (new XmlParser()).parse(srcGwtDir.toString()+"/" +gwtModule.replaceAll('\\.','/')+'.gwt.xml')
                        gwtRename = moduleXml['@rename-to']
                        if (gwtRename==null){
                            gwtRename=gwtModule
                        }
                    }
                }
                def testDir = p.file("${moduleFolder}/test")
                def requiresTest = testDir.exists()
                if (requiresTest){
                    p.sourceSets.test.compileClasspath += p.files(p.sourceSets[moduleName].java.outputDir) { builtBy p.sourceSets[moduleName].compileJavaTaskName }
                    p.sourceSets.test.runtimeClasspath += p.files(p.sourceSets[moduleName].java.outputDir) { builtBy p.sourceSets[moduleName].compileJavaTaskName }
                    p.sourceSets.test.java.srcDir "${moduleName}/test"
                }
                def moduleDependencies=[]
                def moduleVersion = p.version
                if (manifestFile.exists()){
                    def parsedManifest= (new XmlParser()).parse("${moduleFolder}/resources/manifest.xml")
                    parsedManifest.module[0].dependencies[0].dependency.each{ dep ->
                        moduleDependencies.add(dep.@name)
                    }
                    moduleVersion = parsedManifest.module[0].version[0].text()
                }
                if (requiresJar.toBoolean()) {
                    p.task([type: Jar],"jar_$moduleName") {
                        ext.moduleName = moduleName
                        ext.moduleVersion= moduleVersion
                        manifest {
                            attributes 'Implementation-Title': p.project_nice_name, 'Implementation-Version': moduleVersion
                        }
                        from p.sourceSets[moduleName].output
                        from ("$moduleFolder") { include "META-INF/**" }
                        from ("$staticFolder") { into "OPENCMS" }
                        if (gwtModule != null){
                            from( "${p.buildDir}/gwt/${moduleName}") {
                                exclude '**/WEB-INF/**'
                                into "OPENCMS/gwt"
                            }
                        }
                        archiveName moduleName+'.jar'
                        baseName moduleName
                        exclude '**/.gitignore'
                        exclude '**/test/**'
                        doFirst {
                            println HORIZONTAL_LINE
                            println "Building JAR for $moduleName version $moduleVersion"
                            println HORIZONTAL_LINE
                        }
                    }
                }
                if (requiresTest.toBoolean()) {
                    p.task([type: Test, dependsOn: p.sourceSets.test.compileJavaTaskName], "test_$moduleName") {
                        useJUnit()
                        classpath += p.sourceSets.test.compileClasspath
                        classpath += p.files(p.sourceSets.test.java.outputDir)
                        include "**/Test*"
                        // important: exclude all anonymous classes
                        exclude '**/*$*.class'
                        scanForTestClasses false
                        testClassesDirs = p.files(p.sourceSets.test.java.outputDir)
                        systemProperties['db.product'] = "hsqldb"
                        systemProperties['test.data.path'] = "${p.projectDir}/test/data"
                        systemProperties['test.webapp.path'] = "${p.projectDir}/test/webapp"
                        systemProperties['test.build.folder'] =p.sourceSets.test.output.resourcesDir
                        maxHeapSize = p.max_heap_size
                        jvmArgs '-XX:MaxPermSize=256m'
                        testLogging.showStandardStreams = true
                        ignoreFailures true
                    }
                }
                p.task([type: Zip], "dist_$moduleName"){
                    ext.moduleName = moduleName
                    ext.moduleFolder = moduleFolder
                    ext.dependencies = moduleDependencies
                    ext.gwtSourceSetName = gwtSourceSetName
                    ext.gwtRenameTo = gwtRename
                    ext.requiresJar = requiresJar
                    ext.requiresTest = requiresTest
                    if (p.hasProperty('noVersion')) {
                        version
                        p.modulesAll +="${moduleName}.zip,"
                    } else {
                        version moduleVersion
                        p.modulesAll +="${moduleName}-${moduleVersion}.zip,"
                    }
                    destinationDir p.modulesDistsDir
                    baseName moduleName
                    doFirst {
                        println HORIZONTAL_LINE
                        println "Building ZIP for $moduleName version $moduleVersion"
                        println HORIZONTAL_LINE
                    }
                    // excluding jars from modules, jars will be placed in the WEB-INF lib folder through the deployment process
                    from("${moduleFolder}/resources"){
                        exclude '**/lib*/*.jar'
                        exclude 'manifest.xml'
                    }
                    // the following allows to rename the .categories to _categories for instances still using the legacy folder name
                    from("${moduleFolder}/resources"){
                        include 'manifest.xml'
                        if (p.hasProperty('replaceCategoryFolder')){
                            filter { line -> line.replaceAll('/.categories', '/_categories') }
                        }
                    }
                }
                if (requiresJar.toBoolean()) {
                    p.tasks["dist_$moduleName"].dependsOn("jar_$moduleName")
                    p.moduleLibs+="${moduleName}.jar,"
                }

                if (gwtModule != null){
                    p.logger.lifecycle("creating sourceset for $gwtModule")

                    p.sourceSets.create(gwtSourceSetName,{
                        java {
                            srcDirs srcGwtDir
                            srcDir srcModule
                            exclude '**/test/**'
                        }
                        resources {
                            srcDirs srcGwtDir
                        }
                    })
                    p.sourceSets[gwtSourceSetName].compileClasspath=p.configurations.compile
                    p.task([dependsOn: p.tasks["${gwtSourceSetName}Classes"], type: JavaExec], "gwt_$moduleName") {
                        ext.buildDir =  p.buildDir.toString()  +"/gwt/$moduleName"
                        ext.extraDir =  p.buildDir.toString() + "/extra/$moduleName"
                        ext.moduleName = moduleName
                        inputs.files p.sourceSets[gwtSourceSetName].java.srcDirs
                        inputs.dir p.sourceSets[gwtSourceSetName].output.resourcesDir
                        outputs.dir buildDir

                        // Workaround for incremental build (GRADLE-1483)
                        outputs.upToDateSpec = new org.gradle.api.specs.AndSpec()

                        doFirst {
                            println HORIZONTAL_LINE
                            println "Building GWT resources for $gwtModule"
                            println HORIZONTAL_LINE
                            // to clean the output directory, delete it first
                            def dir = p.file(buildDir)
                            if (dir.exists()){
                                p.delete(dir)
                            }
                            dir.mkdirs()
                        }

                        main = 'com.google.gwt.dev.Compiler'

                        classpath {
                            [
                                p.sourceSets[moduleName].java.srcDirs,
                                p.sourceSets[moduleName].compileClasspath,
                                p.sourceSets[gwtSourceSetName].java.srcDirs,
                                p.sourceSets[gwtSourceSetName].output.resourcesDir,
                                p.sourceSets[gwtSourceSetName].java.outputDir
                            ]
                        }


                        args = [
                            gwtModule,
                            // Your GWT module
                            '-war',
                            buildDir,
                            '-logLevel',
                            'ERROR',
                            '-localWorkers',
                            '2',
                            '-style',
                            p.gwtStyle,
                            '-extra',
                            extraDir,
                            "-${p.gwtMode}"
                        ]

                        maxHeapSize = p.max_heap_size
                    }

                    p.tasks["jar_$moduleName"].dependsOn p.tasks["gwt_$moduleName"]
                }
            }

            p.task([type: Copy], 'copyDeps'){
                from p.configurations.moduleDeps
                into "${p.buildDir}/deps"
            }

            p.task([type: Copy], 'copyProjectProps'){
                p.configurations.moduleDeps.each {d ->
                    p.dependencyLibs="${p.dependencyLibs}${d.name},"
                }
                from(p.projectDir) {
                    include 'p.properties'
                    filter(ReplaceTokens, tokens: [
                        MODULES_ALL: ''+p.modulesAll,
                        MODULE_LIBS: ''+p.moduleLibs,
                        DEPENDENCY_LIBS: ''+p.dependencyLibs
                    ])

                }
                into p.modulesDistsDir
            }

            p.task([dependsOn: [
                    p.copyDeps,
                    p.copyProjectProps
                ]], 'bindist') {
                doFirst{
                    println 'Done'
                }
            }

            p.task([type: Jar], 'projectAllJar'){
                p.allModuleNames.each{ moduleName ->
                    from p.sourceSets[moduleName].output
                }
                baseName "${p.project_name}"
                exclude '**/.gitignore'
                exclude '**/test/**'
                doFirst {
                    println HORIZONTAL_LINE
                    println "Building JAR for ${p.project_nice_name} ALL"
                    println HORIZONTAL_LINE
                }
            }

            p.task([type: Javadoc], 'projectAllJavadoc'){
                p.allModuleNames.each{ moduleName ->
                    source += p.sourceSets[moduleName].allJava
                    classpath += p.sourceSets[moduleName].compileClasspath
                }
                destinationDir = p.file("${p.buildDir}/docs/projectAllJavadoc")
                options.addStringOption("sourcepath", "")
            }

            p.task([dependsOn: p.projectAllJavadoc, type: Jar], 'projectAllJavadocJar') {
                classifier 'javadoc'
                from "${p.buildDir}/docs/projectAllJavadoc"
                baseName "${p.project_name}"
            }

            p.task([type: Jar], 'projectAllSourcesJar') {
                p.allModuleNames.each{ moduleName ->
                    from p.sourceSets[moduleName].allSource
                }
                classifier 'sources'
                baseName "${p.project_name}"
            }

            p.tasks.findAll{ task -> task.name.startsWith('dist_')}.each{ dist_task ->
                dist_task.dependencies.each{ dep ->
                    def depCompileName = 'compile'+dep.replaceAll('\\.','')+'java'
                    p.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals(depCompileName)}.each {comp_task ->
                        p.sourceSets[dist_task.moduleName].compileClasspath += p.files(p.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                        if (dist_task.gwtSourceSetName!=null){
                            p.sourceSets["${dist_task.gwtSourceSetName}"].compileClasspath += p.files(p.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                        }
                        if (dist_task.requiresTest.toBoolean()){
                            p.sourceSets.test.compileClasspath += p.files(p.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                            p.sourceSets.test.runtimeClasspath += p.files(p.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                        }
                    }
                }

                if (dist_task.requiresJar.toBoolean()){
                    p.tasks['jar_'+dist_task.moduleName].dependsOn{
                        p.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals('compile'+dist_task.moduleName.replaceAll('\\.','')+'java')}
                    }
                }
                p.bindist.dependsOn dist_task
                p.projectAllJar.dependsOn{
                    p.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals('compile'+dist_task.moduleName.replaceAll('\\.','')+'java')}
                }
            }

            // fixed issue with libraries containing both .java and .class files
            p.tasks.withType(JavaCompile) {
                options.sourcepath=p.files()
                options.encoding='UTF-8'
            }
            p.tasks.withType(Javadoc) {
                options.addStringOption("sourcepath", "")
                if (JavaVersion.current().isJava8Compatible()) {
                    options.addStringOption("Xdoclint:none", "-quiet")
                    options.addBooleanOption("-allow-script-in-comments",true);
                }
            }

            p.artifacts {
                archives p.projectAllJar
                archives p.projectAllSourcesJar
                archives p.projectAllJavadocJar
            }

            p.install {
                repositories {
                    mavenInstaller {
                        addFilter("${p.project_name}"){artifact, file ->
                            artifact.name.startsWith("${p.project_name}")
                        }
                        pom("${p.project_name}").project {
                            name "${p.project_nice_name} all"
                            description "${p.project_nice_name} all modules"
                            packaging 'jar'
                            groupId 'com.alkacon'
                            url 'http://www.alkacon.com'
                            version p.build_version
                            licenses {
                                license {
                                    name 'GNU General Public License'
                                    url 'http://www.gnu.org/licenses/gpl.html'
                                    distribution 'repo'
                                }
                            }
                            organization {
                                name 'Alkacon Software'
                                url 'http://www.alkacon.com'
                            }
                            developers {
                                developer {
                                    name 'Alkacon Software'
                                    url 'http://www.alkacon.com'
                                }
                            }
                        }
                    }
                }
            }

            p.task([type: Wrapper], 'setGradleVersion'){
                gradleVersion = GRADLE_VERSION
            }
        }catch(Exception e) {
            p.logger.error("${HORIZONTAL_LINE}\nFailed to configure OpenCms modules project:\n\n${e.message} \n\nUse the opencmsPluginDescription task to view plugin description.\n${HORIZONTAL_LINE}\n\n",e)
        }
    }
}
