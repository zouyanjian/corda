package net.corda.rpc

import net.corda.core.contracts.DummyContract
import net.corda.core.messaging.RPCOps
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.testing.ledger
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommandLineRPCParserTest {
    class Nums(val first: Int = 0, val second: Int = 0)

    interface TestTarget : RPCOps {
        fun simple()
        fun simple2(num: Int, str: String)
        fun add(nums: Nums): Int

        fun buildTx(withTime: Instant): WireTransaction
    }

    class TestTargetImpl : TestTarget {
        override val protocolVersion: Int = 1

        @JvmField var simpleWasRun = false
        override fun simple() {
            simpleWasRun = true
        }

        @JvmField var simple2Num = -1
        @JvmField var simple2Str = ""
        override fun simple2(num: Int, str: String) {
            simple2Num = num
            simple2Str = str
        }

        override fun add(nums: Nums): Int = nums.first + nums.second

        override fun buildTx(withTime: Instant): WireTransaction {
            var wtx: WireTransaction? = null
            ledger {
                unverifiedTransaction {
                    output("some output", DummyContract.SingleOwnerState(1234, DUMMY_PUBKEY_1))
                }
                wtx = transaction {
                    input("some output")
                    output(DummyContract.SingleOwnerState(5678, DUMMY_PUBKEY_1))
                    verifies()
                }
            }
            return wtx!!
        }
    }

    val target = TestTargetImpl()
    val parser = CommandLineRPCParser(TestTarget::class.java)

    @Test
    fun `no arguments and no return value`() {
        parser.parse("simple", target)()
        assertTrue(target.simpleWasRun)
    }

    @Test
    fun `simple arguments and no return values`() {
        parser.parse("simple2 num: 10, str: yeah yeah yeah", target)()
        assertEquals(10, target.simple2Num)
        assertEquals("yeah yeah yeah", target.simple2Str)
    }

    @Test
    fun `simple and object arguments with simple return value`() {
        assertEquals(8, parser.parse("add nums: {first: 5, second: 3}", target)())
    }

    @Test
    fun `complex parameter and return`() {
        assertEquals("WireTransaction", parser.parse("""buildTx withTime: "2016-12-20T17:59:08Z"""", target)()!!.javaClass.simpleName)
    }

    @Test
    fun exceptions() {
        assertFailsWith<CommandLineRPCParser.UnparseableRPCException.MissingParameter> { parser.parse("add xnums: {first: 5, second: 10}", null) }
        assertFailsWith<CommandLineRPCParser.UnparseableRPCException.MissingParameter> { parser.parse("add", null) }
        assertEquals("nosuchmethod", assertFailsWith<CommandLineRPCParser.UnparseableRPCException.UnknownMethod> { parser.parse("nosuchmethod 1234", null) }.methodName)
    }
}