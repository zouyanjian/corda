package com.r3corda.uniqueness

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory;

class UniquenessService {

    fun operate() {
        // create an Akka uniqueness
        val system = ActorSystem.create("UniquenessSystem", ConfigFactory.load("uniqueness"))
        val uniquenessValidator = system.actorOf(Props.create(UniquenessValidatorAkkaPersistence::class.java), "uniqueness-validator")
    }

    companion object {
        // Main
        @JvmStatic fun main(args: Array<String>) {
            val uniquenessService = UniquenessService()
            uniquenessService.operate()
        }
    }

}