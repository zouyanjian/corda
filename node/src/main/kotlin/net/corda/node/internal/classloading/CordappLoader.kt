package net.corda.node.internal.classloading

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult
import net.corda.core.contracts.Contract
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import kotlin.reflect.KClass

data class Cordapp(
        val contractClassNames: List<String>,
        val initiatedFlows: List<Class<out FlowLogic<*>>>,
        val rpcFlows: List<Class<out FlowLogic<*>>>)

/**
 * Handles CorDapp loading and classpath scanning of CorDapp JARs
 *
 * @property cordappJarPaths The classpath of cordapp JARs
 */
class CordappLoader private constructor (private val cordappJarPaths: List<URL>) {
    @VisibleForTesting
    internal val appClassLoader: ClassLoader = javaClass.classLoader
    private val scanResult = scanCordapps()

    companion object {
        private val logger = loggerFor<CordappLoader>()

        /**
         * Creates a default CordappLoader intended to be used in non-dev or non-test environments.
         *
         * @param   basedir The directory that this node is running in. Will use this to resolve the plugins directory
         *                  for classpath scanning.
         */
        fun createDefault(baseDir: Path): CordappLoader {
            val pluginsDir = baseDir / "plugins"
            return CordappLoader(if (!pluginsDir.exists()) emptyList<URL>() else pluginsDir.list {
                it.filter { it.isRegularFile() && it.toString().endsWith(".jar") }.collect(Collectors.toList()).map { it.toUri().toURL() }
            })
        }

        /**
         * Creates a dev mode CordappLoader intended to only be used in test environments.
         *
         * @param   scanPackage Resolves the JARs that contain scanPackage and use them as the source for
         *                      the classpath scanning.
         */
        fun createDevMode(scanPackage: String): CordappLoader {
            val resource = scanPackage.replace('.', '/')
            val paths = javaClass.classLoader.getResources(resource)
                    .asSequence()
                    .map {
                        val uri = if (it.protocol == "jar") {
                            (it.openConnection() as JarURLConnection).jarFileURL.toURI()
                        } else {
                            URI(it.toExternalForm().removeSuffix(resource))
                        }
                        uri.toURL()
                    }
                    .toList()
            return CordappLoader(paths)
        }

        /**
         * Creates a dev mode CordappLoader intended only to be used in test environments
         *
         * @param   scanJars    Uses the JAR URLs provided for classpath scanning and Cordapp detection
         */
        fun createDevMode(scanJars: List<URL>): CordappLoader {
            return CordappLoader(scanJars)
        }
    }

    fun findCordapps(): List<Cordapp> {
        return cordappJarPaths.map { scanCordapp(it) }.map {
            Cordapp(findContractClassNames(it), findInitiatedFlows(it), findRPCFlows(it))
        }
    }

    fun findServices(info: NodeInfo): List<Class<out SerializeAsToken>> {
        fun getServiceType(clazz: Class<*>): ServiceType? {
            return try {
                clazz.getField("type").get(null) as ServiceType
            } catch (e: NoSuchFieldException) {
                logger.warn("${clazz.name} does not have a type field, optimistically proceeding with install.")
                null
            }
        }

        return scanResult?.getClassesWithAnnotation(SerializeAsToken::class, CordaService::class)
                ?.filter {
                    val serviceType = getServiceType(it)
                    if (serviceType != null && info.serviceIdentities(serviceType).isEmpty()) {
                        logger.debug {
                            "Ignoring ${it.name} as a Corda service since $serviceType is not one of our " +
                                    "advertised services"
                        }
                        false
                    } else {
                        true
                    }
                } ?: emptyList<Class<SerializeAsToken>>()
    }

    private fun findInitiatedFlows(scanResult: ScanResult?): List<Class<out FlowLogic<*>>> {
        return scanResult?.getClassesWithAnnotation(FlowLogic::class, InitiatedBy::class)
                // First group by the initiating flow class in case there are multiple mappings
                ?.groupBy { it.requireAnnotation<InitiatedBy>().value.java }
                ?.map { (initiatingFlow, initiatedFlows) ->
                    val sorted = initiatedFlows.sortedWith(FlowTypeHierarchyComparator(initiatingFlow))
                    if (sorted.size > 1) {
                        logger.warn("${initiatingFlow.name} has been specified as the inititating flow by multiple flows " +
                                "in the same type hierarchy: ${sorted.joinToString { it.name }}. Choosing the most " +
                                "specific sub-type for registration: ${sorted[0].name}.")
                    }
                    sorted[0]
                } ?: emptyList<Class<out FlowLogic<*>>>()
    }

    private fun findRPCFlows(scanResult: ScanResult?): List<Class<out FlowLogic<*>>> {
        fun Class<out FlowLogic<*>>.isUserInvokable(): Boolean {
            return Modifier.isPublic(modifiers) && !isLocalClass && !isAnonymousClass && (!isMemberClass || Modifier.isStatic(modifiers))
        }

        val found = scanResult?.getClassesWithAnnotation(FlowLogic::class, StartableByRPC::class)?.filter { it.isUserInvokable() } ?: emptyList<Class<out FlowLogic<*>>>()
        val coreFlows = listOf(ContractUpgradeFlow.Initiator::class.java)
        return found + coreFlows
    }

    private fun findContractClassNames(scanResult: ScanResult?): List<String> {
        return scanResult?.getNamesOfClassesImplementing(Contract::class.java)!!
    }

    private fun scanCordapps(): ScanResult? {
        logger.info("Scanning CorDapps in $cordappJarPaths")
        return if (cordappJarPaths.isNotEmpty())
            FastClasspathScanner().addClassLoader(appClassLoader).overrideClasspath(cordappJarPaths).scan()
        else
            null
    }

    private fun scanCordapp(cordappJarPath: URL): ScanResult? {
        logger.info("Scanning CorDapp in $cordappJarPaths")
        return if (cordappJarPaths.isNotEmpty())
            FastClasspathScanner().addClassLoader(appClassLoader).overrideClasspath(cordappJarPath).scan()
        else
            null
    }

    private class FlowTypeHierarchyComparator(val initiatingFlow: Class<out FlowLogic<*>>) : Comparator<Class<out FlowLogic<*>>> {
        override fun compare(o1: Class<out FlowLogic<*>>, o2: Class<out FlowLogic<*>>): Int {
            return if (o1 == o2) {
                0
            } else if (o1.isAssignableFrom(o2)) {
                1
            } else if (o2.isAssignableFrom(o1)) {
                -1
            } else {
                throw IllegalArgumentException("${initiatingFlow.name} has been specified as the initiating flow by " +
                        "both ${o1.name} and ${o2.name}")
            }
        }
    }

    private fun <T : Any> ScanResult.getClassesWithAnnotation(type: KClass<T>, annotation: KClass<out Annotation>): List<Class<out T>> {
        fun loadClass(className: String): Class<out T>? {
            return try {
                appClassLoader.loadClass(className) as Class<T>
            } catch (e: ClassCastException) {
                logger.warn("As $className is annotated with ${annotation.qualifiedName} it must be a sub-type of ${type.java.name}")
                null
            } catch (e: Exception) {
                logger.warn("Unable to load class $className", e)
                null
            }
        }

        return getNamesOfClassesWithAnnotation(annotation.java)
                .mapNotNull { loadClass(it) }
                .filterNot { Modifier.isAbstract(it.modifiers) }
    }
}
