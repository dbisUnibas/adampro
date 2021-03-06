package org.vitrivr.adampro.process

import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import org.vitrivr.adampro.communication.rpc.{DataDefintion, DataQuery}
import org.vitrivr.adampro.grpc.grpc._
import org.vitrivr.adampro.utils.Logging

import scala.concurrent.ExecutionContext

/**
  * adamtwo
  *
  * Ivan Giangreco
  * March 2016
  */
class RPCStartup(port : Int) extends Thread with Logging {
  override def run(): Unit = {
    try {
      log.trace("RPC server starting up")

      val server = new RPCServer(port)(scala.concurrent.ExecutionContext.global)
      server.start()

      log.debug("RPC server running")

      server.blockUntilShutdown()
    } catch {
      case e: Exception => log.error("exception in RPC", e)
    }
  }
}

private class RPCServer(port : Int)(executionContext: ExecutionContext) {
  self =>
  private var server: Server = null

  def start(): Unit = {
    server = NettyServerBuilder.forPort(port)
      .addService(AdamDefinitionGrpc.bindService(new DataDefintion, executionContext))
      .addService(AdamSearchGrpc.bindService(new DataQuery, executionContext))
      .maxMessageSize(12582912)
      .build.start
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        self.stop()
      }
    })
  }

  def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  def blockUntilShutdown(): Unit = {
    while (true) {
      Thread.sleep(1000)
    }
  }
}
