package net.corda.irs.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.parseNetworkHostAndPort
import net.corda.finance.plugin.registerFinanceJSONMappers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service

@SpringBootApplication
class IrsDemoWebApplication {

    @Bean
    fun rpcClient(): CordaRPCOps {
        println("MAKING RPC CLIENT!")
        return CordaRPCClient("localhost:10006".parseNetworkHostAndPort()).start("user", "password").proxy
    }

    @Bean
    fun objectMapper(@Autowired cordaRPCOps: CordaRPCOps): ObjectMapper {
        val mapper = JacksonSupport.createDefaultMapper(cordaRPCOps)
        registerFinanceJSONMappers(mapper)
        return mapper
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(IrsDemoWebApplication::class.java, *args)
}
