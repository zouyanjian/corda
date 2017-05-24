package net.corda.plugins.bintray

import org.gradle.util.ConfigureUtil

open class BintrayConfigExtension {
    /**
     * Bintray username
     */
    var user: String? = null
    /**
     * Bintray access key
     */
    var key: String? = null
    /**
     * Bintray repository
     */
    var repo: String? = null
    /**
     * Bintray organisation
     */
    var org: String? = null
    /**
     * Licenses for packages uploaded by this configuration
     */
    var licenses: Array<String>? = null
    /**
     * Whether to sign packages uploaded by this configuration
     */
    var gpgSign: Boolean? = null
    /**
     * The passphrase for the key used to sign releases.
     */
    var gpgPassphrase: String? = null
    /**
     * VCS URL
     */
    var vcsUrl: String? = null
    /**
     * Project URL
     */
    var projectUrl: String? = null
    /**
     * The publications that will be uploaded as a part of this configuration. These must match both the name on
     * bintray and the gradle module name. ie; it must be "some-package" as a gradle sub-module (root project not
     * supported, this extension is to improve multi-build bintray uploads). The publication must also be called
     * "some-package". Only one publication can be uploaded per module (a bintray plugin restriction(.
     * If any of these conditions are not met your package will not be uploaded.
     */
    var publications: Array<String>? = null
    /**
     * Whether to test the publication without uploading to bintray.
     */
    var dryRun: Boolean? = null
    /**
     * The license this project will use (currently limited to one)
     */
    var license = License()
    /**
     * The developer of this project (currently limited to one)
     */
    var developer = Developer()

    fun license(configure: License.() -> Unit) {
        license.configure()
    }

    fun developer(configure: Developer.() -> Unit) {
        developer.configure()
    }
}