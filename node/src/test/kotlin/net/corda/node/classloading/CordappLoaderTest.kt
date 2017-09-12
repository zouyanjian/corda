package net.corda.node.classloading

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.node.internal.classloading.Cordapp
import net.corda.node.internal.classloading.CordappLoader
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths

class DummyFlow : FlowLogic<Unit>() {
    override fun call() { }
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow : FlowLogic<Unit>() {
    override fun call() { }
}

class CordappLoaderTest {
    @Test
    fun `test that classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = CordappLoader.createDefault(Paths.get("."))
        Assert.assertTrue(loader.findCordapps().isEmpty())
    }

    @Test
    fun `test that classes that are in a cordapp are loaded`() {
        val loader = CordappLoader.createDevMode("net.corda.node.classloading")
        val initiatedFlows = loader.findCordapps().first().initiatedFlows
        val expectedClass = loader.appClassLoader.loadClass("net.corda.node.classloading.LoaderTestFlow")
        Assert.assertNotNull(initiatedFlows.find { it == expectedClass })
    }

    @Test
    fun `isolated JAR contains a CorDapp with a contract`() {
        val isolatedJAR = CordappLoaderTest::class.java.getResource("isolated.jar")!!
        val loader = CordappLoader.createDevMode(listOf(isolatedJAR))
        val expectedCordapp = Cordapp(
                listOf("net.corda.finance.contracts.isolated.AnotherDummyContract"),
                emptyList(),
                listOf(loader.appClassLoader.loadClass("net.corda.core.flows.ContractUpgradeFlow\$Initiator") as Class<FlowLogic<*>>))
        val expected = arrayOf(expectedCordapp)
        Assert.assertArrayEquals(expected, loader.findCordapps().toTypedArray())
    }
}