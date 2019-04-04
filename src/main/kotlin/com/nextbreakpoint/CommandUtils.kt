package com.nextbreakpoint

import com.google.common.io.ByteStreams.copy
import com.nextbreakpoint.flinkclient.api.DefaultApi
import io.kubernetes.client.PortForward
import io.kubernetes.client.models.V1Pod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

object CommandUtils {
    fun flinkApi(host: String = "localhost", port: Int = 8081): DefaultApi {
        val flinkApi = DefaultApi()
        flinkApi.apiClient.basePath = "http://$host:$port"
        flinkApi.apiClient.httpClient.setConnectTimeout(20000, TimeUnit.MILLISECONDS)
        flinkApi.apiClient.httpClient.setWriteTimeout(30000, TimeUnit.MILLISECONDS)
        flinkApi.apiClient.httpClient.setReadTimeout(30000, TimeUnit.MILLISECONDS)
        flinkApi.apiClient.isDebugging = true
        return flinkApi
    }

    @ExperimentalCoroutinesApi
    fun forwardPort(pod: V1Pod?, localPort: Int, stop: Channel<Int>): Thread {
        return Thread(
            Runnable {
                var stdout : Thread? = null
                var stdin : Thread? = null
                try {
                    val forwardResult = PortForward().forward(pod, listOf(8081))
                    val serverSocket = ServerSocket(localPort)
                    val clientSocket = serverSocket.accept()
                    stop.invokeOnClose {
                        try {
                            clientSocket.close()
                        } catch (e: Exception) {
                        }
                        try {
                            serverSocket.close()
                        } catch (e: Exception) {
                        }
                    }
                    stdout = Thread(
                        Runnable {
                            try {
                                copy(clientSocket.inputStream, forwardResult.getOutboundStream(8081))
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        })
                    stdin = Thread(
                        Runnable {
                            try {
                                copy(forwardResult.getInputStream(8081), clientSocket.outputStream)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        })
                    stdout.start()
                    stdin.start()
                    stdout.join()
                    stdin.interrupt()
                    stdin.join()
                    stdout = null
                    stdin = null
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    stdout?.interrupt()
                    stdin?.interrupt()
                    stdout?.join()
                    stdin?.join()
                }
            })
    }

    @Throws(InterruptedException::class)
    fun processExec(proc: Process) {
        var stdout : Thread? = null
        var stderr : Thread? = null
        try {
            stdout = Thread(
                Runnable {
                    try {
                        copy(proc.inputStream, System.out)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                })
            stderr = Thread(
                Runnable {
                    try {
                        copy(proc.errorStream, System.out)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                })
            stdout.start()
            stderr.start()
            proc.waitFor(60, TimeUnit.SECONDS)
            stdout.join()
            stderr.join()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stdout?.interrupt()
            stderr?.interrupt()
            stdout?.join()
            stderr?.join()
        }
    }
}