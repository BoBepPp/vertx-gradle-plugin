package org.vertx.gradle

import org.gradle.api.*
import org.gradle.api.artifacts.*;
import org.gradle.api.logging.*;
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Zip

import groovy.json.*

import java.nio.file.Files

import org.vertx.java.core.Handler
import org.vertx.java.platform.PlatformLocator
import org.vertx.java.platform.impl.ModuleClassLoader

class VertxModulePlugin implements Plugin<Project> {
  def logger = Logging.getLogger(VertxModulePlugin.class)

  void apply(Project project) {
    project.with {
      ext.vertx = true

      apply plugin: PropertiesLoader

      println "Configuring Module: $it"

      apply plugin: 'eclipse'
      apply plugin: 'idea'

      defaultTasks = ['assemble']

      configurations {
        provided
        testCompile.extendsFrom provided
      }

      repositories {
        mavenLocal()
        maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
        mavenCentral()
      }

      dependencies {
        provided "io.vertx:vertx-core:${vertxVersion}"
        provided "io.vertx:vertx-platform:${vertxVersion}"
        testCompile "junit:junit:${junitVersion}"
        testCompile "io.vertx:testtools:${toolsVersion}"
      }

      if (file('src/main/java').isDirectory()) {
        apply plugin: 'java'
        sourceCompatibility = '1.7'
        targetCompatibility = '1.7'

        sourceSets {
          main {
            compileClasspath = compileClasspath + configurations.provided
          }
        }
      }

      if (file('src/main/groovy').isDirectory()) {
        apply plugin: 'groovy'

        sourceSets {
          main {
            compileClasspath = compileClasspath + configurations.provided
          }
        }
      }

      if (file('src/main/scala').isDirectory()) {
        apply plugin: 'scala'

        sourceSets {
          main {
            compileClasspath = compileClasspath + configurations.provided
          }
        }
      }

      loadDefaults(it)
      loadModuleProperties(it)
      loadModuleConfig(it)
      loadBuildScript(it)

      ext.moduleName = "${repotype}:${group}:${artifact}:${version}"
      ext.isRunnable = config.main != null

      defaultTasks = ['assemble']

      task('copyMod', dependsOn: 'classes', description: 'Assemble the module into the local mods directory') << {
        // Copy into module directory
        copy {

          into rootProject.file("mods/$moduleName")
          from compileJava
          from file('src/main/resources')

          // and then into module library directory
          into( 'lib' ) {
            from configurations.compile.copy {
              if (it instanceof ProjectDependency) {
                return it.dependencyProject.isLibrary
              } else {
                return true
              }
            }
          }

        }
      }

      task('modZip', type: Zip, dependsOn: 'pullInDeps', description: 'Package the module .zip file') {
        group = 'vert.x'
        description = "Assembles a vert.x module"
        destinationDir = file("$buildDir/libs")
        archiveName = "${artifact}-${version}.zip"
        from tasks.copyMod
      }

      task('pullInDeps', dependsOn: 'copyMod', description: 'Pull in all the module dependencies for the module into the nested mods directory') << {
        if (pullInDeps == 'true') {
          def pm = PlatformLocator.factory.createPlatformManager()
          System.out.println("Pulling in dependencies for module ${properites.moduleName}. Please wait...")
          pm.pullInDependencies(moduleName)
          System.out.println("Dependencies pulled into mods directory of module")
        }
      }

      // run task
      if (isRunnable == true) {
        task("run-${artifact}", dependsOn: 'copyMod', description: 'Run the module using all the build dependencies (not using installed vertx)') << {
          def mutex = new Object()

          ModuleClassLoader.reverseLoadOrder = false
          def pm = PlatformLocator.factory.createPlatformManager()
          pm.deployModule(moduleName, null, 1, new Handler<String>() {
            public void handle(String deploymentID) {
              if (!deploymentID){
                logger.error 'Verticle failed to deploy.'

                // Wake the main thread
                synchronized(mutex){
                  mutex.notify()
                }
                return
              }

              logger.info "Verticle deployed! Deployment ID is: $deploymentID"
              logger.info 'CTRL-C to stop server'
            }
          });

          // Waiting thread so that Verticle will continue running
          synchronized (mutex){
            mutex.wait()
          }
        }
      }

      if(repotype == 'maven'){
        apply plugin: MavenSettings
      }
    }
  }

  def loadDefaults(Project project){
    project.loadProperties(
      [
        group: 'my-company',
        artifact: project.name,
        version: '1.0.0-SNAPSHOT',
        repotype: 'local',

        isModule: false,
        isLibrary: false
      ]
    )

    if (project.file('module.gradle').exists()){
      project.isModule = true
    } else {
      project.isLibrary = true
    }
  }

  def loadModuleConfig(Project project){
    def f = project.file('src/main/resources/mod.json')
    if(!f.canRead()){
      project.ext.config = [:]
      return
    }

    f.withReader { def reader ->
      project.ext.config = new JsonSlurper().parse(reader)
    }
  }

  def loadModuleProperties(Project project){
    def f = project.file('module.properties')
    if(!f.canRead()){
      return [:]
    }

    f.withReader { def reader ->
      def props = new Properties()
      props.load(reader)

      props.each { k,v ->
        if (project.hasProperty(k)){
          project[k] = v
        } else {
          project.ext[k] = v
        }
      }
    }
  }

  def loadBuildScript(Project project) {
    def f = project.file('module.gradle')
    if(!f.canRead()){
      return [:]
    }

    project.apply from: project.file('module.gradle')
  }

}