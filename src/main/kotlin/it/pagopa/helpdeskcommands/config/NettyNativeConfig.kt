package it.pagopa.helpdeskcommands.config

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NettyNativeConfig {

    /**
     * Registers JCTools and Netty classes for native compilation reflection. This resolves the
     * NoClassDefFoundError for MpscUnpaddedArrayQueue and related classes that Netty uses for
     * buffer allocation in native images.
     *
     * Note: We only register the key classes that are public and accessible. JCTools classes are
     * shaded into Netty, so we use the shaded package paths.
     */
    @Bean
    @RegisterReflectionForBinding(
        // Main Netty buffer allocation classes - these are public and accessible
        io.netty.buffer.PooledByteBufAllocator::class,
        io.netty.util.internal.PlatformDependent::class,
        // The specific JCTools class mentioned in the pipeline health check error (shaded path)
        io.netty.util.internal.shaded.org.jctools.queues.unpadded.MpscUnpaddedArrayQueue::class,
        io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue::class
    )
    fun nettyNativeConfiguration(): String {
        // Simple bean to trigger the reflection registration
        return "netty-native-config"
    }
}
