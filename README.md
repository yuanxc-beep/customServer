# AndroidServer

[![@Tony沈哲 on weibo](https://img.shields.io/badge/weibo-%40Tony%E6%B2%88%E5%93%B2-blue.svg)](http://www.weibo.com/fengzhizi715)
[![License](https://img.shields.io/badge/license-Apache%202-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![](https://jitpack.io/v/fengzhizi715/AndroidServer.svg)](https://jitpack.io/#fengzhizi715/AndroidServer)

基于 Kotlin + Netty 开发，为 Android App 提供 Server 的功能，包括 Http、TCP、WebSocket 服务

# Feature:

* 支持 Http、TCP、WebSocket 服务
* 支持 Rest 风格的 API、文件上传、下载
* 支持加载静态网页
* Http 的路由表、全局的 HttpFilter 均采用字典树(Tried Tree)实现
* 日志隔离，开发者可以使用自己的日志库
* core 模块只依赖 netty-all，不依赖其他第三方库

# 最新版本

模块|最新版本
---|:-------------:
android-server-core|[![](https://jitpack.io/v/fengzhizi715/AndroidServer.svg)](https://jitpack.io/#fengzhizi715/AndroidServer)
android-server-converter-gson|[![](https://jitpack.io/v/fengzhizi715/AndroidServer.svg)](https://jitpack.io/#fengzhizi715/AndroidServer)

# 下载安装

将它添加到项目的 root build.gradle 中：

```groovy
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

Gradle:

```groovy
implementation 'com.github.fengzhizi715.AndroidServer:core:<latest-version>'
```

```groovy
implementation 'com.github.fengzhizi715.AndroidServer:gson:<latest-version>'
```

# Usage:

## 搭建 Http 服务

AndroidServer 的 http 服务本身支持 rest 风格、支持跨域、cookies 等。

```kotlin
fun startHttpServer(context:Context, androidServer:AndroidServer) {

    androidServer
        .get("/hello") { _, response: Response ->
            response.setBodyText("hello world")
        }
        .get("/sayHi/{name}") { request, response: Response ->
            val name = request.param("name")
            response.setBodyText("hi $name!")
        }
        .post("/uploadLog") { request, response: Response ->
            val requestBody = request.content()
            response.setBodyText(requestBody)
        }
        .get("/downloadFile") { request, response: Response ->
            val fileName = "xxx.txt"
            File("/sdcard/$fileName").takeIf { it.exists() }?.let {
                response.sendFile(it.readBytes(),fileName,"application/octet-stream")
            }?: response.setBodyText("no file found")
        }
        .get("/test") { _, response: Response ->
            response.html(context,"test")
        }
        .fileUpload("/uploadFile") { request, response: Response -> // curl -v -F "file=@/Users/tony/1.png" 10.184.18.14:8080/uploadFile

            val uploadFile = request.file("file")
            val fileName = uploadFile.fileName
            val f = File("/sdcard/$fileName")
            val byteArray = uploadFile.content
            f.writeBytes(byteArray)

            response.setBodyText("upload success")
        }
        .filter("/sayHi/*", object : HttpFilter {
            override fun before(request: Request): Boolean {
                LogManager.d("HttpService","before....")
                return true
            }

            override fun after(request: Request, response: Response) {
                LogManager.d("HttpService","after....")
            }

        })
        .start()
}
```

测试：

```
curl -v 127.0.0.1:8080/hello
*   Trying 127.0.0.1...
* Connected to 127.0.0.1 (127.0.0.1) port 8080 (#0)
> GET /hello HTTP/1.1
> Host: 127.0.0.1:8080
> User-Agent: curl/7.50.1-DEV
> Accept: */*
>
< HTTP/1.1 200 OK
< server: monica
< content-type: text/plain
< content-length: 11
<
* Connection #0 to host 127.0.0.1 left intact
hello world
```

```
curl -v -d 测试 127.0.0.1:8080/uploadLog
*   Trying 127.0.0.1...
* Connected to 127.0.0.1 (127.0.0.1) port 8080 (#0)
> POST /uploadLog HTTP/1.1
> Host: 127.0.0.1:8080
> User-Agent: curl/7.50.1-DEV
> Accept: */*
> Content-Length: 6
> Content-Type: application/x-www-form-urlencoded
>
* upload completely sent off: 6 out of 6 bytes
< HTTP/1.1 200 OK
< server: monica
< content-type: text/plain
< content-length: 6
<
* Connection #0 to host 127.0.0.1 left intact
测试
```

## 搭建 WebSocket 服务

AndroidServer 支持提供 WebSocket 服务

```kotlin
fun startWebSocketServer(androidServer:AndroidServer) {
    androidServer
        .websocket("/ws",object : SocketListener<String>{
            override fun onMessageResponseServer(msg: String, ChannelId: String) {
                LogManager.d("WebSocketService","msg = $msg")
            }

            override fun onChannelConnect(channel: Channel) {
                val insocket = channel.remoteAddress() as InetSocketAddress
                val clientIP = insocket.address.hostAddress
                LogManager.d("WebSocketService","connect client: $clientIP")

            }

            override fun onChannelDisConnect(channel: Channel) {
                val ip = channel.remoteAddress().toString()
                LogManager.d("WebSocketService","disconnect client: $ip")
            }

        })
        .start()
}
```

测试：

```
curl -v \
     --include \
     --no-buffer \
     --header "Connection: Upgrade" \
     --header "Upgrade: websocket" \
     --header "Host: echo.websocket.org" \
     --header "Origin: https://echo.websocket.org" \
     --header "Sec-WebSocket-Key: NVwjmQUcWCenfWu98asDmg==" \
     --header "Sec-WebSocket-Version: 13" \
     http://127.0.0.1:8080/ws
```

```
GET /ws HTTP/1.1
Host: echo.websocket.org
User-Agent: curl/7.67.0
Accept: */*
Connection: Upgrade
Upgrade: websocket
Origin: https://echo.websocket.org
Sec-WebSocket-Key: NVwjmQUcWCenfWu98asDmg==
Sec-WebSocket-Version: 13
```

```
HTTP/1.1 101 Switching Protocols
upgrade: websocket
connection: upgrade
sec-websocket-accept: oPhRcOTYgRvrC0D+cTPcN3XYC1k=
```

> Socket/WebSocket 服务可以使用 ：https://github.com/fengzhizi715/NetDiagnose 进行测试。
上述 websocket 服务默认的 endpoint：ws://ip:port/ws


## 搭建 Socket 服务

AndroidServer 支持单独提供 Socket 服务，也支持一个端口同时提供 Socket/WebSocket 服务。

使用 androidServer 的 socketAndWS()，同时提供 Socket/WebSocket 服务：

```kotlin
fun startSocketServer(androidServer:AndroidServer) {
    androidServer
        .socketAndWS("/ws", object: SocketListener<String> {
            override fun onMessageResponseServer(msg: String, ChannelId: String) {
                LogManager.d("SocketService","msg = $msg")
            }

            override fun onChannelConnect(channel: Channel) {
                val insocket = channel.remoteAddress() as InetSocketAddress
                val clientIP = insocket.address.hostAddress
                LogManager.d("SocketService","connect client: $clientIP")

            }

            override fun onChannelDisConnect(channel: Channel) {
                val ip = channel.remoteAddress().toString()
                LogManager.d("SocketService","disconnect client: $ip")
            }

        })
        .start()
}
```

androidServer 的 socket() 单独提供 Socket 服务。

# TODO List：

* 提供默认的 TCP 服务
* 支持 Https
* 反向代理
* 支持 HTTP/2


联系方式
===

Wechat：fengzhizi715


> Java与Android技术栈：每周更新推送原创技术文章，欢迎扫描下方的公众号二维码并关注，期待与您的共同成长和进步。

![](https://github.com/fengzhizi715/NetDiscovery/blob/master/images/gzh.jpeg)


ChangeLog
===
[版本更新记录](CHANGELOG.md)

License
-------

    Copyright (C) 2017 - present, Tony Shen.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
