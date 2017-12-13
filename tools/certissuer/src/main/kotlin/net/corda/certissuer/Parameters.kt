package net.corda.certissuer;

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import joptsimple.ArgumentAcceptingOptionSpec
import joptsimple.OptionParser
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Configuration parameters.
 */
data class Parameters(val dataSourceProperties: Properties,
                      val databaseConfig: DatabaseConfig = DatabaseConfig(),
                      val device: String = DEFAULT_DEVICE,
        // TODO this needs cleaning up after the config-file-only support is implemented
                      val keyGroup: String,
                      val keySpecifier: Int = DEFAULT_KEY_SPECIFIER,
                      val rootPrivateKeyPassword: String,
                      val csrPrivateKeyPassword: String,
                      val csrCertificateName: String = DEFAULT_CSR_CERTIFICATE_NAME,
                      val networkMapCertificateName: String = DEFAULT_NETWORK_MAP_CERTIFICATE_NAME,
                      val networkMapPrivateKeyPassword: String,
                      val rootCertificateName: String = DEFAULT_ROOT_CERTIFICATE_NAME,
                      val validDays: Int,
                      val signAuthThreshold: Int = DEFAULT_SIGN_AUTH_THRESHOLD,
                      val keyGenAuthThreshold: Int = DEFAULT_KEY_GEN_AUTH_THRESHOLD,
                      val authKeyFilePath: Path? = DEFAULT_KEY_FILE_PATH,
                      val authKeyFilePassword: String? = DEFAULT_KEY_FILE_PASSWORD,
                      val autoUsername: String? = DEFAULT_AUTO_USERNAME,
        // TODO Change this to Duration in the future.
                      val signInterval: Long = DEFAULT_SIGN_INTERVAL) {
    companion object {
        val DEFAULT_DEVICE = "3001@127.0.0.1"
        val DEFAULT_SIGN_AUTH_THRESHOLD = 2
        val DEFAULT_KEY_GEN_AUTH_THRESHOLD = 2
        val DEFAULT_CSR_CERTIFICATE_NAME = X509Utilities.CORDA_INTERMEDIATE_CA
        val DEFAULT_ROOT_CERTIFICATE_NAME = X509Utilities.CORDA_ROOT_CA
        val DEFAULT_KEY_SPECIFIER = 1
        val DEFAULT_KEY_FILE_PATH: Path? = null //Paths.get("/Users/michalkit/WinDev1706Eval/Shared/TEST4.key")
        val DEFAULT_KEY_FILE_PASSWORD: String? = null
        val DEFAULT_AUTO_USERNAME: String? = null
        val DEFAULT_NETWORK_MAP_CERTIFICATE_NAME = "cordaintermediateca_nm"
        val DEFAULT_SIGN_INTERVAL = 600L // in seconds (10 minutes)
    }
}

/**
 * Parses the list of arguments and produces an instance of [Parameters].
 * @param args list of strings corresponding to program arguments
 * @return instance of Parameters produced from [args]
 */
fun parseParameters(vararg args: String): Parameters {
    val argConfig = args.toConfigWithOptions {
        accepts("basedir", "Overriding configuration filepath, default to current directory.").withRequiredArg().defaultsTo(".").describedAs("filepath")
        accepts("configFile", "Overriding configuration file.").withRequiredArg().defaultsTo("node.conf").describedAs("filepath")
        accepts("device", "CryptoServer device address").withRequiredArg().defaultsTo(DEFAULT_DEVICE)
        accepts("keyGroup", "CryptoServer key group").withRequiredArg()
        accepts("keySpecifier", "CryptoServer key specifier").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_KEY_SPECIFIER)
        accepts("rootPrivateKeyPassword", "Password for the root certificate private key").withRequiredArg().describedAs("password")
        accepts("csrPrivateKeyPassword", "Password for the CSR signing certificate private key").withRequiredArg().describedAs("password")
        accepts("keyGenAuthThreshold", "Authentication strength threshold for the HSM key generation").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_KEY_GEN_AUTH_THRESHOLD)
        accepts("signAuthThreshold", "Authentication strength threshold for the HSM CSR signing").withRequiredArg().ofType(Int::class.java).defaultsTo(DEFAULT_SIGN_AUTH_THRESHOLD)
        accepts("authKeyFilePath", "Key file path when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg().describedAs("filepath")
        accepts("authKeyFilePassword", "Key file password when authentication is based on a key file (i.e. authMode=${AuthMode.KEY_FILE.name})").withRequiredArg()
        accepts("autoUsername", "Username to be used for certificate signing (if not specified it will be prompted for input)").withRequiredArg()
        accepts("csrCertificateName", "Name of the certificate to be used by this CA to sign CSR").withRequiredArg().defaultsTo(DEFAULT_CSR_CERTIFICATE_NAME)
        accepts("rootCertificateName", "Name of the root certificate to be used by this CA").withRequiredArg().defaultsTo(DEFAULT_ROOT_CERTIFICATE_NAME)
        accepts("validDays", "Validity duration in days").withRequiredArg().ofType(Int::class.java)
        accepts("signInterval", "Time interval (in seconds) in which network map is signed").withRequiredArg().ofType(Long::class.java).defaultsTo(DEFAULT_SIGN_INTERVAL)
    }

    val configFile = if (argConfig.hasPath("configFile")) {
        Paths.get(argConfig.getString("configFile"))
    } else {
        Paths.get(argConfig.getString("basedir")) / "signing_service.conf"
    }

    val config = argConfig.withFallback(ConfigFactory.parseFile(configFile.toFile(), ConfigParseOptions.defaults().setAllowMissing(true))).resolve()
    return config.parseAs()
}

fun Array<out String>.toConfigWithOptions(registerOptions: OptionParser.() -> Unit): Config {
    val parser = OptionParser()
    val helpOption = parser.acceptsAll(listOf("h", "?", "help"), "show help").forHelp()
    registerOptions(parser)
    val optionSet = parser.parse(*this)
    // Print help and exit on help option.
    if (optionSet.has(helpOption)) {
        throw ShowHelpException(parser)
    }
    // Convert all command line options to Config.
    return ConfigFactory.parseMap(parser.recognizedOptions().mapValues {
        val optionSpec = it.value
        if (optionSpec is ArgumentAcceptingOptionSpec<*> && !optionSpec.requiresArgument() && optionSet.has(optionSpec)) true else optionSpec.value(optionSet)
    }.mapKeys { it.key.toCamelcase() }.filterValues { it != null })
}