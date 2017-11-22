package net.corda.plugins

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import groovy.lang.Closure
import net.corda.cordform.CordformNode
import org.apache.commons.io.FilenameUtils
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.model.ObjectFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * Represents a node that will be installed.
 */
open class Node @Inject constructor(private val project: Project) : CordformNode() {
    private data class ResolvedCordapp(val jarFile: File, val config: String?)

    companion object {
        @JvmStatic
        val nodeJarName = "corda.jar"
        @JvmStatic
        val webJarName = "corda-webserver.jar"
        private val configFileProperty = "configFile"
        val capsuleCacheDir: String = "./cache"
    }

    fun fullPath(): Path = project.projectDir.toPath().resolve(nodeDir.toPath())
    fun logDirectory(): Path = fullPath().resolve("logs")
    fun makeLogDirectory() = Files.createDirectories(logDirectory())

    /**
     * Set the list of CorDapps to install to the plugins directory. Each cordapp is a fully qualified Maven
     * dependency name, eg: com.example:product-name:0.1
     *
     * @note Your app will be installed by default and does not need to be included here.
     * @note Type is any due to gradle's use of "GStrings" - each value will have "toString" called on it
     */
    var cordapps: MutableList<Any>
        get() = internalCordapps as MutableList<Any>
        @Deprecated("Use cordapp instead - setter will be removed by Corda V4.0")
        set(value) {
            value.forEach {
                cordapp(it.toString())
            }
        }

    private val internalCordapps = mutableListOf<Cordapp>()
    private val builtCordapp = Cordapp(project)
    private val releaseVersion = project.rootProject.ext<String>("corda_release_version")
    internal lateinit var nodeDir: File

    /**
     * Sets whether this node will use HTTPS communication.
     *
     * @param isHttps True if this node uses HTTPS communication.
     */
    fun https(isHttps: Boolean) {
        config = config.withValue("useHTTPS", ConfigValueFactory.fromAnyRef(isHttps))
    }

    /**
     * Sets the H2 port for this node
     */
    fun h2Port(h2Port: Int) {
        config = config.withValue("h2port", ConfigValueFactory.fromAnyRef(h2Port))
    }

    fun useTestClock(useTestClock: Boolean) {
        config = config.withValue("useTestClock", ConfigValueFactory.fromAnyRef(useTestClock))
    }

    /**
     * Set the HTTP web server port for this node. Will use localhost as the address.
     *
     * @param webPort The web port number for this node.
     */
    fun webPort(webPort: Int) {
        config = config.withValue("webAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$webPort"))
    }

    /**
     * Set the HTTP web server address and port for this node.
     *
     * @param webAddress The web address for this node.
     */
    fun webAddress(webAddress: String) {
        config = config.withValue("webAddress",
                ConfigValueFactory.fromAnyRef(webAddress))
    }

    /**
     * Set the network map address for this node.
     *
     * @warning This should not be directly set unless you know what you are doing. Use the networkMapName in the
     *          Cordform task instead.
     * @param networkMapAddress Network map node address.
     * @param networkMapLegalName Network map node legal name.
     */
    fun networkMapAddress(networkMapAddress: String, networkMapLegalName: String) {
        val networkMapService = mutableMapOf<String, String>()
        networkMapService.put("address", networkMapAddress)
        networkMapService.put("legalName", networkMapLegalName)
        config = config.withValue("networkMapService", ConfigValueFactory.fromMap(networkMapService))
    }

    /**
     * Set the SSHD port for this node.
     *
     * @param sshdPort The SSHD port.
     */
    fun sshdPort(sshdPort: Int) {
        config = config.withValue("sshdAddress",
                ConfigValueFactory.fromAnyRef("$DEFAULT_HOST:$sshdPort"))
    }

    /**
     * Configures the default cordapp automatically added to this node
     *
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(configureClosure: Closure<in Cordapp>): Cordapp {
        project.configure(builtCordapp, configureClosure) as Cordapp
        return builtCordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(coordinates), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @param configureClosure A groovy closure to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project, configureClosure: Closure<in Cordapp>): Cordapp {
        val cordapp = project.configure(Cordapp(cordappProject), configureClosure) as Cordapp
        internalCordapps += cordapp
        return cordapp
    }

    /**
     * Install a cordapp to this node
     *
     * @param cordappProject A project that produces a cordapp JAR
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(cordappProject: Project): Cordapp {
        return Cordapp(cordappProject).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param coordinates The coordinates of the [Cordapp]
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String): Cordapp {
        return Cordapp(coordinates).apply {
            internalCordapps += this
        }
    }

    /**
     * Install a cordapp to this node
     *
     * @param configureFunc A lambda to configure a [Cordapp] object
     * @return The created and inserted [Cordapp]
     */
    fun cordapp(coordinates: String, configureFunc: Cordapp.() -> Unit): Cordapp {
        return Cordapp(coordinates).apply {
            configureFunc()
            internalCordapps += this
        }
    }

    internal fun build() {
        configureProperties()
        installCordaJar()
        if (config.hasPath("webAddress")) {
            installWebserverJar()
        }
        installCordapps()
        installConfig()
        appendOptionalConfig()
    }

    internal fun rootDir(rootDir: Path) {
        if (name == null) {
            project.logger.error("Node has a null name - cannot create node")
            throw IllegalStateException("Node has a null name - cannot create node")
        }

        val dirName = try {
            val o = X500Name(name).getRDNs(BCStyle.O)
            if (o.isNotEmpty()) {
                o.first().first.value.toString()
            } else {
                name
            }
        } catch (_: IllegalArgumentException) {
            // Can't parse as an X500 name, use the full string
            name
        }
        nodeDir = File(rootDir.toFile(), dirName)
    }

    private fun configureProperties() {
        config = config.withValue("rpcUsers", ConfigValueFactory.fromIterable(rpcUsers))
        if (notary != null) {
            config = config.withValue("notary", ConfigValueFactory.fromMap(notary))
        }
        if (extraConfig != null) {
            config = config.withFallback(ConfigFactory.parseMap(extraConfig))
        }
    }

    /**
     * Installs the corda fat JAR to the node directory.
     */
    private fun installCordaJar() {
        val cordaJar = verifyAndGetCordaJar()
        project.copy {
            it.apply {
                from(cordaJar)
                into(nodeDir)
                rename(cordaJar.name, nodeJarName)
                fileMode = Cordformation.executableFileMode
            }
        }
    }

    /**
     * Installs the corda webserver JAR to the node directory
     */
    private fun installWebserverJar() {
        val webJar = verifyAndGetWebserverJar()
        project.copy {
            it.apply {
                from(webJar)
                into(nodeDir)
                rename(webJar.name, webJarName)
            }
        }
    }

    /**
     * Installs other cordapps to this node's cordapps directory.
     */
    private fun installCordapps() {
        val cordapps = getCordappList()
        val cordappsDir = File(nodeDir, "cordapps")
        project.copy {
            it.apply {
                from(cordapps.map { it.jarFile })
                into(project.file(cordappsDir))
            }
        }

        installCordappConfigs(cordapps)
    }

    private fun installCordappConfigs(cordapps: Collection<ResolvedCordapp>) {
        val cordappsDir = project.file(File(nodeDir, "cordapps"))
        cordappsDir.mkdirs()
        cordapps.filter { it.config != null }
                .map { Pair<String, String>("${FilenameUtils.removeExtension(it.jarFile.name)}.conf", it.config!!) }
                .forEach { project.file(File(cordappsDir, it.first)).writeText(it.second) }
    }

    /**
     * Installs the configuration file to this node's directory and detokenises it.
     */
    private fun installConfig() {
        val options = ConfigRenderOptions
                .defaults()
                .setOriginComments(false)
                .setComments(false)
                .setFormatted(true)
                .setJson(false)
        val configFileText = config.root().render(options).split("\n").toList()

        // Need to write a temporary file first to use the project.copy, which resolves directories correctly.
        val tmpDir = File(project.buildDir, "tmp")
        tmpDir.mkdir()
        val tmpConfFile = File(tmpDir, "node.conf")
        Files.write(tmpConfFile.toPath(), configFileText, StandardCharsets.UTF_8)

        project.copy {
            it.apply {
                from(tmpConfFile)
                into(nodeDir)
            }
        }
    }

    /**
     * Appends installed config file with properties from an optional file.
     */
    private fun appendOptionalConfig() {
        val optionalConfig: File? = when {
            project.findProperty(configFileProperty) != null -> //provided by -PconfigFile command line property when running Gradle task
                File(project.findProperty(configFileProperty) as String)
            config.hasPath(configFileProperty) -> File(config.getString(configFileProperty))
            else -> null
        }

        if (optionalConfig != null) {
            if (!optionalConfig.exists()) {
                project.logger.error("$configFileProperty '$optionalConfig' not found")
            } else {
                val confFile = File(project.buildDir.path + "/../" + nodeDir, "node.conf")
                confFile.appendBytes(optionalConfig.readBytes())
            }
        }
    }

    /**
     * Find the corda JAR amongst the dependencies.
     *
     * @return A file representing the Corda JAR.
     */
    private fun verifyAndGetCordaJar(): File {
        val maybeCordaJAR = project.configuration("runtime").filter {
            it.toString().contains("corda-$releaseVersion.jar") || it.toString().contains("corda-enterprise-$releaseVersion.jar")
        }
        if (maybeCordaJAR.isEmpty) {
            throw RuntimeException("No Corda Capsule JAR found. Have you deployed the Corda project to Maven? Looked for \"corda-$releaseVersion.jar\"")
        } else {
            val cordaJar = maybeCordaJAR.singleFile
            assert(cordaJar.isFile)
            return cordaJar
        }
    }

    /**
     * Find the corda JAR amongst the dependencies
     *
     * @return A file representing the Corda webserver JAR
     */
    private fun verifyAndGetWebserverJar(): File {
        val maybeJar = project.configuration("runtime").filter {
            it.toString().contains("corda-webserver-$releaseVersion.jar")
        }
        if (maybeJar.isEmpty) {
            throw RuntimeException("No Corda Webserver JAR found. Have you deployed the Corda project to Maven? Looked for \"corda-webserver-$releaseVersion.jar\"")
        } else {
            val jar = maybeJar.singleFile
            assert(jar.isFile)
            return jar
        }
    }

    /**
     * Gets a list of cordapps based on what dependent cordapps were specified.
     *
     * @return List of this node's cordapps.
     */
    private fun getCordappList(): Collection<ResolvedCordapp> =
            internalCordapps.map { cordapp -> resolveCordapp(cordapp) } + resolveBuiltCordapp()

    private fun resolveCordapp(cordapp: Cordapp): ResolvedCordapp {
        val cordappConfiguration = project.configuration("cordapp")
        val cordappName = if(cordapp.project != null) cordapp.project.name else cordapp.coordinates
        val cordappFile = cordappConfiguration.files {
            when {
                it is ProjectDependency -> it.dependencyProject == cordapp.project!!
                cordapp.coordinates != null -> {
                    // Cordapps can sometimes contain a GString instance which fails the equality test with the Java string
                    @Suppress("RemoveRedundantCallsOfConversionMethods")
                    val coordinates = cordapp.coordinates.toString()
                    coordinates == (it.group + ":" + it.name + ":" + it.version)
                }
                else -> false
            }
        }

        return when {
            cordappFile.size == 0 -> throw GradleException("Cordapp $cordappName not found in cordapps configuration.")
            cordappFile.size > 1 -> throw GradleException("Multiple files found for $cordappName")
            else -> ResolvedCordapp(cordappFile.single(), cordapp.config)
        }
    }

    private fun resolveBuiltCordapp(): ResolvedCordapp {
        val projectCordappFile = project.tasks.getByName("jar").outputs.files.singleFile
        return ResolvedCordapp(projectCordappFile, builtCordapp.config)
    }
}
