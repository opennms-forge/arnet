package org.opennms.arnet.kafka2ws

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("kafka2ws")
                .mainClass(Application.javaClass)
                .start()
    }

}