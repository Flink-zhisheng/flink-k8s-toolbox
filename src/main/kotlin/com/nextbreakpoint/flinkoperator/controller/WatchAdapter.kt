package com.nextbreakpoint.flinkoperator.controller

import com.nextbreakpoint.flinkoperator.common.utils.KubeClient
import io.kubernetes.client.JSON
import io.kubernetes.client.util.Watch
import io.vertx.core.Context
import org.apache.log4j.Logger
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

class WatchAdapter(val json: JSON, val kubeClient: KubeClient) {
    companion object {
        private val logger: Logger = Logger.getLogger(WatchAdapter::class.simpleName)
    }

    fun watchFlinkClusters(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchFlickClusters(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/flinkcluster/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/flinkcluster/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh FlinkClusters resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/flinkcluster/deleteAll", "")
                        val resources = kubeClient.listFlinkClusters(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/flinkcluster/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    fun watchServices(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchServices(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/service/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/service/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh Services resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/service/deleteAll", "")
                        val resources = kubeClient.listServiceResources(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/service/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    fun watchDeployments(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchDeployments(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/deployment/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/deployment/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh Deployments resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/deployment/deleteAll", "")
                        val resources = kubeClient.listDeploymentResources(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/deployment/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    fun watchJobs(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchJobs(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/job/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/job/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh Jobs resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/job/deleteAll", "")
                        val resources = kubeClient.listJobResources(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/job/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    fun watchStatefulSets(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchStatefulSets(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/statefulset/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/statefulset/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh StatefulSets resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/statefulset/deleteAll", "")
                        val resources = kubeClient.listStatefulSetResources(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/statefulset/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    fun watchPersistentVolumeClaims(context: Context, namespace: String) {
        thread {
            watchResources(namespace, {
                kubeClient.watchPermanentVolumeClaims(it)
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/persistentvolumeclaim/change", json.serialize(resource))
                }
            }, { resource ->
                context.runOnContext {
                    context.owner().eventBus().publish("/resource/persistentvolumeclaim/delete", json.serialize(resource))
                }
            }, { namespace ->
                logger.debug("Refresh PersistentVolumeClaims resources...")
                context.runOnContext {
                    try {
                        context.owner().eventBus().publish("/resource/persistentvolumeclaim/deleteAll", "")
                        val resources = kubeClient.listPermanentVolumeClaimResources(namespace)
                        resources.forEach { resource ->
                            context.owner().eventBus().publish("/resource/persistentvolumeclaim/change", json.serialize(resource))
                        }
                    } catch (e: Exception) {
                        logger.error("An error occurred while listing resources", e)
                    }
                }
            })
        }
    }

    private fun <T> watchResources(
        namespace: String,
        createResourceWatch: (String) -> Watch<T>,
        onChangeResource: (T) -> Unit,
        onDeleteResource: (T) -> Unit,
        onReloadResources: (String) -> Unit
    ) {
        while (true) {
            try {
                onReloadResources(namespace)
                createResourceWatch(namespace).use {
                    it.forEach { resource ->
                        when (resource.type) {
                            "ADDED", "MODIFIED" -> {
                                onChangeResource(resource.`object`)
                            }
                            "DELETED" -> {
                                onDeleteResource(resource.`object`)
                            }
                        }
                    }
                }
                Thread.sleep(1000L)
            } catch (e: InterruptedException) {
                break
            } catch (e: RuntimeException) {
                if (e.cause !is SocketTimeoutException) {
                    logger.error("An error occurred while watching a resource", e)
                    Thread.sleep(5000L)
                }
            } catch (e: Exception) {
                logger.error("An error occurred while watching a resource", e)
                Thread.sleep(5000L)
            }
        }
    }
}