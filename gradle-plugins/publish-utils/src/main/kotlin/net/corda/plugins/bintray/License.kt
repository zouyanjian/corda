package net.corda.plugins.bintray

class License {
    /**
     * The name of license (eg; Apache 2.0)
     */
    var name: String? = null
    /**
     * URL to the full license file
     */
    var url: String? = null
    /**
     * The distribution level this license corresponds to (eg: repo)
     */
    var distribution: String? = null
}