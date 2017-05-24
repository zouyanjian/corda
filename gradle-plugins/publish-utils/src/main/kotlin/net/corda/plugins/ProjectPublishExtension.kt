package net.corda.plugins

open class ProjectPublishExtension {
    /**
     * Use a different name from the current project name for publishing
     */
    var name: String? = null
    /**
     * True when we do not want to publish default Java components
     */
    var disableDefaultJar = false
    /**
     * True if publishing a WAR instead of a JAR. Forces disableDefaultJAR to "true" when true
     */
    var publishWar = false
}