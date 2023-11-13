package com.safframework.server.core.handler.http

import com.safframework.server.core.http.HttpRequest
import com.safframework.server.core.http.HttpResponse
import com.safframework.server.core.log.LogManager
import com.safframework.server.core.router.RouteTable
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.FullHttpRequest

/**
 *
 * @FileName:
 *          com.safframework.server.core.handler.http.H1BrokerHandler
 * @author: Tony Shen
 * @date: 2020-03-24 16:25
 * @version: V1.0 <描述当前版本功能>
 */
class H1BrokerHandler(private val routeRegistry: RouteTable): ChannelInboundHandlerAdapter() {

    @Throws(Exception::class)
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is FullHttpRequest) {

            val request = HttpRequest(msg)

            val filter = routeRegistry.getFilter(request)

            val before = filter?.let { it.before(request)}?:true

            if (!before) {
                ctx.channel().writeAndFlush(HttpResponse.errorH1Response()).addListener(ChannelFutureListener.CLOSE)
                return
            }

            val response = routeRegistry.getHandler(request).let {
                val impl = it.invoke(request, HttpResponse(ctx.channel())) as HttpResponse
                filter?.after(request,impl)
                impl.buildFullH1Response()
            }

            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        } else {
            LogManager.w("H1BrokerHandler","unknown message type $msg")
        }
        ctx.fireChannelRead(msg)
    }
}