package net.corda.plugins

import org.gradle.api.*
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.PublishingExtension
import net.corda.plugins.bintray.*
import com.jfrog.bintray.gradle.BintrayExtension

/**
 * A utility plugin that when applied will automatically create source and javadoc publishing tasks
 * To apply this plugin you must also add 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.4' to your
 * buildscript's classpath dependencies.
 *
 * To use this plugin you can add a new configuration block (extension) to your root build.gradle. See the fields
 * in BintrayConfigExtension.
 */
class PublishTasks : Plugin<Project> {
    private lateinit var project: Project
    private lateinit var publishName: String
    private lateinit var publishConfig: ProjectPublishExtension

    override fun apply(project: Project) {
        this.project = project

        createTasks()
        createExtensions()
        createConfigurations()

        project.afterEvaluate {
            configurePublishingName()
            checkAndConfigurePublishing()
        }
    }

    private fun configurePublishingName() {
        if(publishConfig.name != null) {
            project.logger.info("Changing publishing name for ${project.name} to ${publishConfig.name}")
            publishName = publishConfig.name!!
        } else {
            publishName = project.name
        }
    }

    private fun checkAndConfigurePublishing() {
        project.logger.info("Checking whether to publish $publishName")
        val bintrayConfig = project.rootProject.extensions.findByType(BintrayConfigExtension::class.java)
        if((bintrayConfig?.publications != null) && (bintrayConfig.publications!!.filter { it == publishName }.size > 0)) {
            configurePublishing(bintrayConfig)
        }
    }

    private fun configurePublishing(bintrayConfig: BintrayConfigExtension) {
        project.logger.info("Configuring bintray for ${publishName}")
        configureMavenPublish(bintrayConfig)
        configureBintray(bintrayConfig)
    }

    private fun configureMavenPublish(bintrayConfig: BintrayConfigExtension) {
        project.apply(mapOf("plugin" to "maven-publish"))
        val publishing = project.extensions.findByType(PublishingExtension::class.java)!!
        publishing.publications.create(publishName, MavenPublication::class.java) { publication: MavenPublication ->
            publication.apply {
                val componentType = if(!publishConfig.disableDefaultJar && !publishConfig.publishWar) { "java" } else { "web" }
                groupId = project.group.toString()
                artifactId = publishName

                from(project.components.getByName(componentType))
                artifact(project.tasks.getByName("sourceJar"))
                artifact(project.tasks.getByName("javadocJar"))

                project.configurations.getByName("publish").artifacts.forEach { artifact ->
                    project.logger.debug("Adding artifact: $artifact")
                    artifact(artifact)
                }

                extendPomForMavenCentral(pom, bintrayConfig)
            }
        }

        project.task(mapOf("dependsOn" to "publishToMavenLocal"), "install")
    }

    // Maven central requires all of the below fields for this to be a valid POM
    private fun extendPomForMavenCentral(pom: MavenPom, config: BintrayConfigExtension) {
        pom.withXml {
            val node = it.asNode().children().last() as groovy.util.Node
            node.apply {
                appendNode("name", publishName)
                appendNode("description", project.description)
                appendNode("url", config.projectUrl)
                appendNode("scm").apply {
                    appendNode("url", config.vcsUrl)
                }
                appendNode("licenses").apply {
                    appendNode("license").apply {
                        appendNode("name", config.license.name)
                        appendNode("url", config.license.url)
                        appendNode("distribution", config.license.url)
                    }
                }
                appendNode("developers").apply {
                    appendNode("developer").apply {
                        appendNode("id", config.developer.id)
                        appendNode("name", config.developer.name)
                        appendNode("email", config.developer.email)
                    }
                }
            }
        }
    }

    private fun configureBintray(bintrayConfig: BintrayConfigExtension) {
        project.apply(mapOf("plugin" to "com.jfrog.bintray"))
        project.extensions.findByType(BintrayExtension::class.java)!!.apply {
            user = bintrayConfig.user
            key = bintrayConfig.key
            println(publications.javaClass)
            require(publications != null)
            publications[publications.size] = publishName
            dryRun = bintrayConfig.dryRun ?: false
            pkg.apply {
                repo = bintrayConfig.repo
                name = publishName
                userOrg = bintrayConfig.org
                bintrayConfig.licenses?.forEach {
                    licenses[licenses.size] = it
                }

                version.apply {
                    gpg.apply {
                        sign = bintrayConfig.gpgSign ?: false
                        passphrase = bintrayConfig.gpgPassphrase
                    }
                }
            }
        }
    }

    private fun createTasks() {
        if(project.hasProperty("classes")) {
            val task = project.task(mapOf("type" to Jar::class.java, "dependsOn" to project.tasks.getByName("classes")), "sourceJar") as Jar
            task.apply {
                classifier = "sources"
                val sourceSets = project.properties.get("sourceSets") as SourceSetContainer
                from(sourceSets.getByName("main").allSource)
            }
        }

        if(project.hasProperty("javadoc")) {
            val task = project.task(mapOf("type" to Jar::class.java, "dependsOn" to project.tasks.getByName("javadoc")), "javadocJar") as Jar
            task.apply {
                classifier = "javadoc"
                from((project.tasks.findByName("javadoc") as Javadoc).destinationDir)
            }
        }
    }

    private fun createExtensions() {
        if(project == project.rootProject) {
            project.extensions.create("bintrayConfig", BintrayConfigExtension::class.java)
        }
        publishConfig = project.extensions.create("publish", ProjectPublishExtension::class.java)
    }

    private fun createConfigurations() {
        project.configurations.create("publish")
    }
}
