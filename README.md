# OpenCms Gradle plugins

## Description
This Gradle plugin can be used to build modules for OpenCms.

It assumes each module is placed in a folder named as the module within the project folder.

This module folder should contain a 'resources' folder containing all module VFS resource
including the module manifest.xml file.

Optionally the module folder may contain a 'src' folder containing the java source file
required by the module.

Optionally it may contain a 'static' folder containing static resources that will be placed
into the module JAR file and will be accessible through static resource URLs within OpenCms.

All module names of the project must be listed in the build property 'modules_list' as comma
separated values.

Also the artifact name needs be provided with the build property 'project_name' as well as the
project nice name with 'project_nice_name' and the build version with 'build_version'.

Use the task 'bindist' to build all project modules and 'install' to make project artifacts
available within your local maven cache.