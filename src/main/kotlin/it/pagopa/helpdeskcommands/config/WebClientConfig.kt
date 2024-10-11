package it.pagopa.helpdeskcommands.config

import io.netty.channel.ChannelOption
import io.netty.channel.epoll.EpollChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectRequestDto
import it.pagopa.generated.helpdeskcommands.model.RefundRedirectResponseDto
import it.pagopa.generated.npg.api.PaymentServicesApi
import it.pagopa.generated.npg.model.ClientErrorDto
import it.pagopa.generated.npg.model.RefundRequestDto
import it.pagopa.generated.npg.model.RefundResponseDto
import it.pagopa.generated.npg.model.ServerErrorDto
import it.pagopa.helpdeskcommands.client.NodeForwarderClient
import java.util.concurrent.TimeUnit
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.Connection
import reactor.netty.http.client.HttpClient

@Configuration
class WebClientConfig {

    @Bean
    @RegisterReflectionForBinding(
        RefundRequestDto::class,
        RefundResponseDto::class,
        ServerErrorDto::class,
        ClientErrorDto::class
    )
    fun npgWebClient(
        @Value("\${npg.uri}") baseUrl: String,
        @Value("\${npg.readTimeout}") readTimeout: Int,
        @Value("\${npg.connectionTimeout}") connectionTimeout: Int,
        @Value("\${npg.tcp.keepAlive.enabled}") tcpKeepAliveEnabled: Boolean,
        @Value("\${npg.tcp.keepAlive.idle}") tcpKeepAliveIdle: Int,
        @Value("\${npg.tcp.keepAlive.intvl}") tcpKeepAliveIntvl: Int,
        @Value("\${npg.tcp.keepAlive.cnt}") tcpKeepAliveCnt: Int
    ): PaymentServicesApi {
        val httpClient =
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .option(ChannelOption.SO_KEEPALIVE, tcpKeepAliveEnabled)
                .option(EpollChannelOption.TCP_KEEPIDLE, tcpKeepAliveIdle)
                .option(EpollChannelOption.TCP_KEEPINTVL, tcpKeepAliveIntvl)
                .option(EpollChannelOption.TCP_KEEPCNT, tcpKeepAliveCnt)
                .doOnConnected { connection: Connection ->
                    connection.addHandlerLast(
                        ReadTimeoutHandler(readTimeout.toLong(), TimeUnit.MILLISECONDS)
                    )
                }
                .resolver { it.ndots(1) }
        val webClient =
            it.pagopa.generated.npg.ApiClient.buildWebClientBuilder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .build()
        val apiClient = it.pagopa.generated.npg.ApiClient(webClient).setBasePath(baseUrl)
        return PaymentServicesApi(apiClient)
    }

    @Bean
    fun nodeForwarderRedirectApiClient(
        @Value("\${node.forwarder.apiKey}") apiKey: String,
        @Value("\${node.forwarder.url}") backendUrl: String,
        @Value("\${node.forwarder.readTimeout}") readTimeout: Int,
        @Value("\${node.forwarder.connectionTimeout}") connectionTimeout: Int
    ): NodeForwarderClient<RefundRedirectRequestDto, RefundRedirectResponseDto> {
        return NodeForwarderClient(apiKey, backendUrl, readTimeout, connectionTimeout)
    }
}
