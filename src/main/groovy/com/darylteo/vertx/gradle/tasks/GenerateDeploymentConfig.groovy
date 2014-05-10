package com.darylteo.vertx.gradle.tasks

import com.darylteo.vertx.gradle.deployments.Deployment
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GenerateDeploymentConfig extends DefaultTask {

  Deployment deployment
  def outputFile

  public setDeployment(Deployment deployment) {
    this.deployment = deployment

    this.inputs.property('config', {
      JsonBuilder json = new JsonBuilder()
      json.call(deployment.config)
      return json.toPrettyString()
    })

    this.outputFile = { "${project.buildDir}/configs/${deployment.name}.conf" }
    this.outputs.file outputFile
  }

  public GenerateDeploymentConfig() {
  }

  @TaskAction
  public def run() {
    def file = project.file(outputFile)
    def dir = file.parentFile

    file.delete()

    if (dir.isDirectory() || dir.mkdirs()) {
      file << JsonOutput.toJson(deployment.config)
    } else {
      throw new Exception("Could not create directory: $dir")
    }
  }
}
