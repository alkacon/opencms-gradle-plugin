
package org.opencms

import org.apache.tools.ant.filters.ReplaceTokens

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.wrapper.Wrapper

class OpenCmsGradlePlugin implements Plugin<Project> {

    private static final String GRADLE_VERSION="6.1"

    void apply(Project project) {

        if (project.hasProperty('build_directory')) {
            project.buildDir = project.build_directory
        }

        if (project.hasProperty('java_target_version')) {
            project.sourceCompatibility = project.java_target_version
            project.targetCompatibility = project.java_target_version
        }

        project.ext.gwtStyle='obfuscated'
        if (project.hasProperty('gwt_style')) {
            project.gwtStyle=project.gwt_style
        }

        project.ext.gwtMode='strict'
        if (project.hasProperty('gwt_mode')) {
            project.gwtMode=project.gwt_mode
        }


        project.repositories {
            mavenLocal()
            if (project.hasProperty('additional_repositories')){
                project.additional_repositories.split(';').each{ repo ->
                    maven { url repo }
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

        project.configurations {
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

        project.apply([from: 'dependencies.gradle'])

        project.sourceSets {
            test {
                java {
                    srcDir "test/src"
                }
                compileClasspath=project.configurations.testCompile
            }
        }


        project.ext.modulesDistsDir = project.file("${project.buildDir}/modulesZip")
        project.ext.moduleLibs=''
        project.ext.dependencyLibs=''
        project.ext.modulesAll=''

        if (!project.hasProperty('max_heap_size')){
            project.ext.max_heap_size='1024m'
        }

        project.ext.allModuleNames = project.modules_list.split(',')
        project.ext.modulesDistsDir = project.file("${project.buildDir}/modulesZip")

        // iterate all configured modules
        project.allModuleNames.each{ moduleName ->
            project.sourceSets.create(moduleName,{
                java {
                    srcDir "${moduleName}/src"
                    exclude '**/test/**'
                }
                resources.srcDir "${moduleName}/src"
            });
            project.sourceSets[moduleName].compileClasspath=project.configurations.compile
            def moduleFolder = project.file("${moduleName}")
            def srcModule = project.file("${moduleFolder}/src")
            def srcGwtDir = project.file("${moduleFolder}/src-gwt")
            def staticFolder=project.file("${moduleFolder}/static")
            def requiresJar = srcModule.exists() || srcGwtDir.exists() || staticFolder.exists()
            def manifestFile = project.file("${moduleFolder}/resources/manifest.xml")
            def gwtModule = null
            def gwtSourceSetName = null
            def propertyFile = project.file("${moduleFolder}/module.properties")
            def gwtRename = null
            if (propertyFile.exists()){
                project.logger.lifecycle("checking properties for module $moduleName")
                Properties moduleProperties= new Properties()
                moduleProperties.load(new FileInputStream(propertyFile))
                if (moduleProperties['module.gwt']!=null){
                    gwtModule = moduleProperties['module.gwt']
                    gwtSourceSetName = moduleName.replace(".", "_")+'_gwt'
                    project.logger.lifecycle("found GWT module $gwtModule")
                    def moduleXml = (new XmlParser()).parse(srcGwtDir.toString()+"/" +gwtModule.replaceAll('\\.','/')+'.gwt.xml')
                    gwtRename = moduleXml['@rename-to']
                    if (gwtRename==null){
                        gwtRename=gwtModule
                    }
                }
            }
            def testDir = project.file("${moduleFolder}/test")
            def requiresTest = testDir.exists()
            if (requiresTest){
                project.sourceSets.test.compileClasspath += project.files(project.sourceSets[moduleName].java.outputDir) { builtBy project.sourceSets[moduleName].compileJavaTaskName }
                project.sourceSets.test.runtimeClasspath += project.files(project.sourceSets[moduleName].java.outputDir) { builtBy project.sourceSets[moduleName].compileJavaTaskName }
                project.sourceSets.test.java.srcDir "${moduleName}/test"
            }
            def moduleDependencies=[]
            def moduleVersion = project.version
            if (manifestFile.exists()){
                def parsedManifest= (new XmlParser()).parse("${moduleFolder}/resources/manifest.xml")
                parsedManifest.module[0].dependencies[0].dependency.each{ dep ->
                    moduleDependencies.add(dep.@name)
                }
                moduleVersion = parsedManifest.module[0].version[0].text()
            }
            if (requiresJar.toBoolean()) {
                project.task([type: Jar],"jar_$moduleName") {
                    ext.moduleName = moduleName
                    ext.moduleVersion= moduleVersion
                    manifest {
                        attributes 'Implementation-Title': project.project_nice_name, 'Implementation-Version': moduleVersion
                    }
                    from project.sourceSets[moduleName].output
                    from ("$moduleFolder") { include "META-INF/**" }
                    from ("$staticFolder") { into "OPENCMS" }
                    if (gwtModule != null){
                        from( "${project.buildDir}/gwt/${moduleName}") {
                            exclude '**/WEB-INF/**'
                            into "OPENCMS/gwt"
                        }
                    }
                    archiveName moduleName+'.jar'
                    baseName moduleName
                    exclude '**/.gitignore'
                    exclude '**/test/**'
                    doFirst {
                        println '======================================================'
                        println "Building JAR for $moduleName version $moduleVersion"
                        println '======================================================'
                    }
                }
            }
            if (requiresTest.toBoolean()) {
                project.task([type: Test, dependsOn: project.sourceSets.test.compileJavaTaskName], "test_$moduleName") {
                    useJUnit()
                    classpath += project.sourceSets.test.compileClasspath
                    classpath += project.files(project.sourceSets.test.java.outputDir)
                    include "**/Test*"
                    // important: exclude all anonymous classes
                    exclude '**/*$*.class'
                    scanForTestClasses false
                    testClassesDirs = project.files(project.sourceSets.test.java.outputDir)
                    systemProperties['db.product'] = "hsqldb"
                    systemProperties['test.data.path'] = "${project.projectDir}/test/data"
                    systemProperties['test.webapp.path'] = "${project.projectDir}/test/webapp"
                    systemProperties['test.build.folder'] =project.sourceSets.test.output.resourcesDir
                    maxHeapSize = project.max_heap_size
                    jvmArgs '-XX:MaxPermSize=256m'
                    testLogging.showStandardStreams = true
                    ignoreFailures true
                }
            }
            project.task([type: Zip], "dist_$moduleName"){
                ext.moduleName = moduleName
                ext.moduleFolder = moduleFolder
                ext.dependencies = moduleDependencies
                ext.gwtSourceSetName = gwtSourceSetName
                ext.gwtRenameTo = gwtRename
                ext.requiresJar = requiresJar
                ext.requiresTest = requiresTest
                if (project.hasProperty('noVersion')) {
                    version
                    project.modulesAll +="${moduleName}.zip,"
                } else {
                    version moduleVersion
                    project.modulesAll +="${moduleName}-${moduleVersion}.zip,"
                }
                destinationDir project.modulesDistsDir
                baseName moduleName
                doFirst {
                    println '======================================================'
                    println "Building ZIP for $moduleName version $moduleVersion"
                    println '======================================================'
                }
                // excluding jars from modules, jars will be placed in the WEB-INF lib folder through the deployment process
                from("${moduleFolder}/resources"){
                    exclude '**/lib*/*.jar'
                    exclude 'manifest.xml'
                }
                // the following allows to rename the .categories to _categories for instances still using the legacy folder name
                from("${moduleFolder}/resources"){
                    include 'manifest.xml'
                    if (project.hasProperty('replaceCategoryFolder')){
                        filter { line -> line.replaceAll('/.categories', '/_categories') }
                    }
                }
            }
            if (requiresJar.toBoolean()) {
                project.tasks["dist_$moduleName"].dependsOn("jar_$moduleName")
                project.moduleLibs+="${moduleName}.jar,"
            }

            if (gwtModule != null){
                project.logger.lifecycle("creating sourceset for $gwtModule")

                project.sourceSets.create(gwtSourceSetName,{
                    java {
                        srcDirs srcGwtDir
                        srcDir srcModule
                        exclude '**/test/**'
                    }
                    resources {
                        srcDirs srcGwtDir
                    }
                })
                project.sourceSets[gwtSourceSetName].compileClasspath=project.configurations.compile
                project.task([dependsOn: project.task["${gwtSourceSetName}Classes"], type: JavaExec], "gwt_$moduleName") {
                    ext.buildDir =  project.buildDir.toString()  +"/gwt/$moduleName"
                    ext.extraDir =  project.buildDir.toString() + "/extra/$moduleName"
                    ext.moduleName = moduleName
                    inputs.files project.sourceSets[gwtSourceSetName].java.srcDirs
                    inputs.dir project.sourceSets[gwtSourceSetName].output.resourcesDir
                    outputs.dir buildDir

                    // Workaround for incremental build (GRADLE-1483)
                    outputs.upToDateSpec = new org.gradle.api.specs.AndSpec()

                    doFirst {
                        println '======================================================'
                        println "Building GWT resources for $gwtModule"
                        println '======================================================'
                        // to clean the output directory, delete it first
                        def dir = project.file(buildDir)
                        if (dir.exists()){
                            delete(dir)
                        }
                        dir.mkdirs()
                    }

                    main = 'com.google.gwt.dev.Compiler'

                    classpath {
                        [
                            project.sourceSets[moduleName].java.srcDirs,
                            project.sourceSets[moduleName].compileClasspath,
                            project.sourceSets[gwtSourceSetName].java.srcDirs,
                            project.sourceSets[gwtSourceSetName].output.resourcesDir,
                            project.sourceSets[gwtSourceSetName].java.outputDir
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
                        project.gwtStyle,
                        '-extra',
                        extraDir,
                        "-${project.gwtMode}"
                    ]

                    maxHeapSize = max_heap_size
                }

                project.tasks["jar_$moduleName"].dependsOn project.tasks["gwt_$moduleName"]
            }
        }

        project.task([type: Copy], 'copyDeps'){
            from project.configurations.moduleDeps
            into "${project.buildDir}/deps"
        }

        project.task([type: Copy], 'copyProjectProps'){
            project.configurations.moduleDeps.each {d ->
                project.dependencyLibs="${project.dependencyLibs}${d.name},"
            }
            from(project.projectDir) {
                include 'project.properties'
                filter(ReplaceTokens, tokens: [
                    MODULES_ALL: ''+project.modulesAll,
                    MODULE_LIBS: ''+project.moduleLibs,
                    DEPENDENCY_LIBS: ''+project.dependencyLibs
                ])

            }
            into project.modulesDistsDir
        }

        project.task([dependsOn: [
                project.copyDeps,
                project.copyProjectProps
            ]], 'bindist') {
            doFirst{
                println 'Done'
            }
        }

        project.task([type: Jar], 'projectAllJar'){
            project.allModuleNames.each{ moduleName ->
                from project.sourceSets[moduleName].output
            }
            baseName "${project.project_name}"
            exclude '**/.gitignore'
            exclude '**/test/**'
            doFirst {
                println '======================================================'
                println "Building JAR for ${project.project_nice_name} ALL"
                println '======================================================'
            }
        }

        project.task([type: Javadoc], 'projectAllJavadoc'){
            project.allModuleNames.each{ moduleName ->
                source += project.sourceSets[moduleName].allJava
                classpath += project.sourceSets[moduleName].compileClasspath
            }
            destinationDir = project.file("${project.buildDir}/docs/projectAllJavadoc")
            options.addStringOption("sourcepath", "")
        }

        project.task([dependsOn: project.projectAllJavadoc, type: Jar], 'projectAllJavadocJar') {
            classifier 'javadoc'
            from "${project.buildDir}/docs/projectAllJavadoc"
            baseName "${project.project_name}"
        }

        project.task([type: Jar], 'projectAllSourcesJar') {
            project.allModuleNames.each{ moduleName ->
                from project.sourceSets[moduleName].allSource
            }
            classifier 'sources'
            baseName "${project.project_name}"
        }

        project.tasks.findAll{ task -> task.name.startsWith('dist_')}.each{ dist_task ->
            dist_task.dependencies.each{ dep ->
                def depCompileName = 'compile'+dep.replaceAll('\\.','')+'java'
                project.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals(depCompileName)}.each {comp_task ->
                    project.sourceSets[dist_task.moduleName].compileClasspath += project.files(project.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                    if (dist_task.gwtSourceSetName!=null){
                        project.sourceSets["${dist_task.gwtSourceSetName}"].compileClasspath += project.files(project.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                    }
                    if (dist_task.requiresTest.toBoolean()){
                        project.sourceSets.test.compileClasspath += project.files(project.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                        project.sourceSets.test.runtimeClasspath += project.files(project.sourceSets[dep].java.outputDir) { builtBy comp_task.name }
                    }
                }
            }

            if (dist_task.requiresJar.toBoolean()){
                project.tasks['jar_'+dist_task.moduleName].dependsOn{
                    project.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals('compile'+dist_task.moduleName.replaceAll('\\.','')+'java')}
                }
            }
            project.bindist.dependsOn dist_task
            project.projectAllJar.dependsOn{
                project.tasks.findAll{ comp_task -> comp_task.name.toLowerCase().equals('compile'+dist_task.moduleName.replaceAll('\\.','')+'java')}
            }
        }

        // fixed issue with libraries containing both .java and .class files
        project.tasks.withType(JavaCompile) {
            options.sourcepath=project.files()
            options.encoding='UTF-8'
        }
        project.tasks.withType(Javadoc) {
            options.addStringOption("sourcepath", "")
            if (JavaVersion.current().isJava8Compatible()) {
                options.addStringOption("Xdoclint:none", "-quiet")
                options.addBooleanOption("-allow-script-in-comments",true);
            }
        }

        project.artifacts {
            archives project.projectAllJar
            archives project.projectAllSourcesJar
            archives project.projectAllJavadocJar
        }

        project.install {
            repositories {
                mavenInstaller {
                    addFilter("${project.project_name}"){artifact, file ->
                        artifact.name.startsWith("${project.project_name}")
                    }
                    pom("${project.project_name}").project {
                        name "${project.project_nice_name} all"
                        description "${project.project_nice_name} all modules"
                        packaging 'jar'
                        groupId 'com.alkacon'
                        url 'http://www.alkacon.com'
                        version project.build_version
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

        project.task([type: Wrapper], 'setGradleVersion'){
            gradleVersion = GRADLE_VERSION
        }
    }
}