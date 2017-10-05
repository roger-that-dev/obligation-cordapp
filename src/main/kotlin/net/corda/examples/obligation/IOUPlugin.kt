//package net.corda.examples.obligation
//
//import net.corda.core.messaging.CordaRPCOps
//import net.corda.core.node.CordaPluginRegistry
//import java.util.function.Function
//
//class IOUPlugin : CordaPluginRegistry() {
//    /**
//     * A list of classes that expose web APIs.
//     */
//    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(net.corda.samples.obligation.api::IOUApi))
//
//    /**
//     * A list of directories in the resources directory that will be served by Jetty under /web.
//     * The template's web frontend is accessible at /web/template.
//     */
//    override val staticServeDirs: Map<String, String> = mapOf(
//            // This will serve the iouWeb directory in resources to /web/template
//            "obligation" to javaClass.classLoader.getResource("iouWeb").toExternalForm()
//    )
//}