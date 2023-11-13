package com.safframework.server.core.handler.http

import com.safframework.server.core.AndroidServer
import com.safframework.server.core.router.RouteTable
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslContext
import io.netty.handler.stream.ChunkedWriteHandler


/**
 *
 * @FileName:
 *          com.safframework.server.core.handler.http.NettyHttpServerInitializer
 * @author: Tony Shen
 * @date: 2020-03-22 16:30
 * @version: V1.0 <描述当前版本功能>
 */
class NettyHttpServerInitializer(
    private val routeRegistry: RouteTable,
    private val sslContext: SslContext?,
    private val builder: AndroidServer.Builder
) : ChannelInitializer<SocketChannel>() {

    @Throws(Exception::class)
    public override fun initChannel(ch: SocketChannel) {

        val pipeline = ch.pipeline()

        sslContext?.let{
//            val alloc = ch.alloc()
//            configureH2(pipeline,alloc,it)
        } ?: {
            configureH1(pipeline)
        }()
    }

    private fun configureH1(pipeline: ChannelPipeline) {
        pipeline
            .addLast("http-codec", HttpServerCodec())
            .addLast("aggregator", HttpObjectAggregator(builder.maxContentLength))
            .addLast("http-chunked", ChunkedWriteHandler()) //用于大数据的分区传输
            .addLast("request-handler", H1BrokerHandler(routeRegistry))
    }

//    private fun configureH2(pipeline: ChannelPipeline, alloc: ByteBufAllocator, sslContext: SslContext) {
//        pipeline.addLast(sslContext.newHandler(alloc), AlpnHandler({
//            this.configureH1(it)
//        }, routeRegistry, builder))
//    }
}