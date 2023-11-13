package com.safframework.server.core.http

import android.content.Context
import com.safframework.server.core.converter.ConverterManager
import com.safframework.server.core.http.cookie.HttpCookie
import com.safframework.server.core.log.LogManager
import com.safframework.server.core.utils.toByteBuf
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.ServerCookieEncoder
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.Http2Headers
import io.netty.util.AsciiString
import io.netty.util.CharsetUtil
import java.io.IOException
import java.io.OutputStream
import java.io.UnsupportedEncodingException

/**
 *
 * @FileName:
 *          com.safframework.server.core.http.HttpResponse
 * @author: Tony Shen
 * @date: 2020-03-24 11:37
 * @version: V1.0 <描述当前版本功能>
 */
class HttpResponse(private val channel:Channel) : Response {

    private var status: HttpResponseStatus? = null
    private var body: ByteBuf? = null
    private var headers: MutableMap<AsciiString, AsciiString> = mutableMapOf()

    override fun setStatus(code:Int): Response {
        this.status = HttpResponseStatus.valueOf(code)
        return this
    }

    override fun setStatus(status: HttpResponseStatus): Response {
        this.status = status
        return this
    }

    override fun setBodyJson(any: Any): Response {
        val byteBuf = channel.alloc().directBuffer()
        try {
            ByteBufOutputStream(byteBuf).use { os: OutputStream ->
                ConverterManager.toJson(any)?.let { os.write(it.toByteArray()) }
                addHeader(HttpHeaderNames.CONTENT_TYPE, JSON)
                body = byteBuf
            }
        } catch (e: IOException) {
            LogManager.e(TAG, e.message?:"error serializing json")
        }
        return this
    }

    override fun setBodyHtml(html: String): Response {
        val bytes = html.toByteArray(CharsetUtil.UTF_8)
        body = toByteBuf(bytes)
        addHeader(HttpHeaderNames.CONTENT_TYPE, TEXT_HTML)
        return this
    }

    override fun setBodyData(contentType: String, data: ByteArray): Response {
        body = Unpooled.copiedBuffer(data)
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
        return this
    }

    override fun setBodyText(text: String): Response {
        val bytes = text.toByteArray(CharsetUtil.UTF_8)
        body = toByteBuf(bytes)
        addHeader(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN)
        return this
    }

    override fun sendFile(bytes: ByteArray , fileName: String , contentType: String): Response {
        var name: String = fileName
        try {
            name = String(fileName.toByteArray(), Charsets.ISO_8859_1)
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        addHeader(HttpHeaderNames.CONTENT_DISPOSITION,ATTACHMENT + name)
        body = Unpooled.copiedBuffer(bytes)
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
        return this
    }

    override fun addHeader(key: CharSequence, value: CharSequence): Response = addHeader(AsciiString.of(key), AsciiString.of(value))

    override fun addHeader(key: AsciiString, value: AsciiString): Response {
        headers[key] = value
        return this
    }

    override fun addCookie(cookie: HttpCookie): Response {
        addHeader(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookie.get()))
        return this
    }

    override fun html(context: Context, view: String): Response = html(context,view,"web")

    override fun html(context: Context, view: String, path: String): Response {

        val list = context.assets.list(path)
        if (list==null || list.isEmpty()) {
            return setBodyText("no $view.html file")
        }

        if (!list.contains("$view.html")) {
            return setBodyText("no $view.html file")
        }

        val inputStream = context.assets.open("$path/$view.html")
        val html = inputStream.bufferedReader().use{ it.readText() }
        return setBodyHtml(html)
    }

    override fun json(context: Context, view: String): Response = json(context,view,"web")

    override fun json(context: Context, view: String, path: String): Response {

        val list = context.assets.list(path)
        if (list==null || list.isEmpty()) {
            return setBodyText("no $view.json file")
        }

        if (!list.contains("$view.json")) {
            return setBodyText("no $view.json file")
        }

        val inputStream = context.assets.open("$path/$view.json")
        val json = inputStream.bufferedReader().use{ it.readText() }
        return setBodyData(JSON.toString(),json.toByteArray())
    }

    override fun image(bytes: ByteArray): Response {
        body = Unpooled.copiedBuffer(bytes)
        addHeader(HttpHeaderNames.CONTENT_TYPE, IMAGE)

        return this
    }

    fun getBody(): ByteBuf = body ?: Unpooled.EMPTY_BUFFER

    private fun buildBodyData(): ByteBuf = body ?: Unpooled.EMPTY_BUFFER

    fun buildFullH1Response(): FullHttpResponse {
        var status = this.status
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status?:HttpResponseStatus.OK, buildBodyData())
        response.headers().set(HttpHeaderNames.SERVER, SERVER_VALUE)
        headers.forEach { (key, value) -> response.headers().set(key, value) }
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buildBodyData().readableBytes())
        return response
    }

    fun buildH2Headers(): Http2Headers {
        var status = this.status
        val http2Headers = DefaultHttp2Headers()
        http2Headers.status(status?.codeAsText()?:HttpResponseStatus.OK.codeAsText())
        http2Headers.set(HttpHeaderNames.SERVER, SERVER_VALUE)
        headers.forEach { (name, value) -> http2Headers.set(name, value) }
        http2Headers.setInt(HttpHeaderNames.CONTENT_LENGTH, buildBodyData().readableBytes())
        return http2Headers
    }

    companion object {
        private val TAG = "HttpResponse"
        private val SERVER_VALUE = AsciiString.cached("monica") // 服务器的名称
        private val JSON = AsciiString.cached("application/json")
        private val TEXT_HTML = AsciiString.cached("text/html")
        private val TEXT_PLAIN = AsciiString.cached("text/plain")
        private val SET_COOKIE = AsciiString.cached("set-cookie")
        private val IMAGE = AsciiString.cached("image/png")
        private val ATTACHMENT = "attachment;filename="

        fun errorH1Response(): FullHttpResponse {

            val bytes = "filter error".toByteArray(CharsetUtil.UTF_8)
            val body = Unpooled.copiedBuffer(bytes)

            return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body).apply {

                headers().set(HttpHeaderNames.SERVER, SERVER_VALUE)
                headers().set(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN)
                headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
            }
        }
    }
}