
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.core.utilities.Emoji
import net.corda.node.utilities.JsonSupport
import net.corda.rpc.Format
import rx.Observable
import rx.Subscriber
import java.io.PrintStream
import java.util.concurrent.CompletableFuture

private object ObservableSerializer : JsonSerializer<Observable<*>>() {
    override fun serialize(value: Observable<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString("(observable)")
    }
}

private fun createOutputMapper(factory: JsonFactory): ObjectMapper {
    return JsonSupport.createDefaultMapper(null, factory).apply({
        // Register serializers for stateful objects from libraries that are special to the RPC system and don't
        // make sense to print out to the screen. For classes we own, annotations can be used instead.
        val rpcModule = SimpleModule("RPC module")
        rpcModule.addSerializer(Observable::class.java, ObservableSerializer)
        registerModule(rpcModule)

        disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        enable(SerializationFeature.INDENT_OUTPUT)
    })
}

private val yamlMapper by lazy { createOutputMapper(YAMLFactory()) }
private val jsonMapper by lazy { createOutputMapper(JsonFactory()) }

fun printAndFollowRPCResponse(outputFormat: Format, response: Any?, toStream: PrintStream = System.out): CompletableFuture<Unit>? {
    val printerFun = when (outputFormat) {
        Format.yaml -> { obj: Any? -> yamlMapper.writeValueAsString(obj) }
        Format.json -> { obj: Any? -> jsonMapper.writeValueAsString(obj) }
        Format.tostring -> { obj: Any? -> Emoji.renderIfSupported { obj.toString() } }
    }
    toStream.println(printerFun(response))
    return maybeFollow(response, printerFun, toStream)
}

private class PrintingSubscriber(private val printerFun: (Any?) -> String, private val toStream: PrintStream) : Subscriber<Any>() {
    private var count = 0;
    val future = CompletableFuture<Unit>()

    init {
        // The future is public and can be completed by something else to indicate we don't wish to follow
        // anymore (e.g. the user pressing Ctrl-C).
        future.thenAccept {
            if (!isUnsubscribed)
                unsubscribe()
        }
        println("Waiting for observations")
    }

    @Synchronized
    override fun onCompleted() {
        toStream.println("Observable has completed")
        future.complete(Unit)
    }

    @Synchronized
    override fun onNext(t: Any?) {
        count++
        toStream.println("Observation $count: " + printerFun(t))
    }

    @Synchronized
    override fun onError(e: Throwable) {
        toStream.println("Observable completed with an error")
        e.printStackTrace()
        future.completeExceptionally(e)
    }
}

// Kotlin bug: USELESS_CAST warning is generated below but the IDE won't let us remove it.
@Suppress("USELESS_CAST", "UNCHECKED_CAST")
private fun maybeFollow(response: Any?, printerFun: (Any?) -> String, toStream: PrintStream): CompletableFuture<Unit>? {
    // Match on a couple of common patterns for "important" observables. It's tough to do this in a generic
    // way because observables can be embedded anywhere in the object graph, and can emit other arbitrary
    // object graphs that contain yet more observables. So we just look for top level responses that follow
    // the standard "track" pattern, and print them until the user presses Ctrl-C
    if (response == null) return null

    val observable: Observable<*> = when (response) {
        is Observable<*> -> response
        is Pair<*, *> -> when {
            response.first is Observable<*> -> response.first as Observable<*>
            response.second is Observable<*> -> response.second as Observable<*>
            else -> null
        }
        else -> null
    } ?: return null

    val subscriber = PrintingSubscriber(printerFun, toStream)
    (observable as Observable<Any>).subscribe(subscriber)
    return subscriber.future
}
