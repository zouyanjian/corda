
import net.corda.rpc.Format
import org.junit.Test
import rx.Observable
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals

class RPCResponsePrinterTest {
    fun check(obj: Any, format: Format, expected: String) {
        val buf = ByteArrayOutputStream()
        printAndFollowRPCResponse(format, obj, PrintStream(buf))
        assertEquals(expected, buf.toString().trim())
    }

    @Test
    fun basic() {
        check(Pair(42, 50), Format.yaml, """---
first: 42
second: 50""")
    }

    @Test
    fun withObservable() {
        check(Observable.from(arrayOf(1, 2, 3)), Format.json, """"(observable)"
Observation 1: 1
Observation 2: 2
Observation 3: 3
Observable has completed""")

        check(Observable.from(arrayOf(1, 2, 3)), Format.yaml, """--- "(observable)"

Observation 1: --- 1

Observation 2: --- 2

Observation 3: --- 3

Observable has completed""")

        check(Pair(123, Observable.empty<Any>()), Format.json, """{
  "first" : 123,
  "second" : "(observable)"
}
Observable has completed""")
    }
}