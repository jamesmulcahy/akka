/**
 * Copyright (C) 2009-2016 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.remote.transport.netty

import akka.AkkaException
import java.nio.channels.ClosedChannelException
import org.jboss.netty.channel._
import scala.util.control.NonFatal

/**
 * INTERNAL API
 */
private[netty] trait NettyHelpers {

  protected def onConnect(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = ()

  protected def onDisconnect(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = ()

  protected def onOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = ()

  protected def onMessage(ctx: ChannelHandlerContext, e: MessageEvent): Unit = ()

  protected def onException(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = ()

  final protected def transformException(ctx: ChannelHandlerContext, ev: ExceptionEvent): Unit = {
    val cause = if (ev.getCause ne null) ev.getCause else new AkkaException("Unknown cause")
    cause match {
      case _: ClosedChannelException ⇒ // Ignore
      case null | NonFatal(_)        ⇒ onException(ctx, ev)
      case e: Throwable              ⇒ throw e // Rethrow fatals
    }
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyServerHelpers extends SimpleChannelUpstreamHandler with NettyHelpers {

  final override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    super.messageReceived(ctx, e)
    onMessage(ctx, e)
  }

  final override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = transformException(ctx, e)

  final override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelConnected(ctx, e)
    onConnect(ctx, e)
  }

  final override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelOpen(ctx, e)
    onOpen(ctx, e)
  }

  final override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelDisconnected(ctx, e)
    onDisconnect(ctx, e)
  }
}

/**
 * INTERNAL API
 */
private[netty] trait NettyClientHelpers extends SimpleChannelHandler with NettyHelpers {
  final override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit = {
    super.messageReceived(ctx, e)
    onMessage(ctx, e)
  }

  final override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = transformException(ctx, e)

  final override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelConnected(ctx, e)
    onConnect(ctx, e)
  }

  final override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelOpen(ctx, e)
    onOpen(ctx, e)
  }

  final override def channelDisconnected(ctx: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    super.channelDisconnected(ctx, e)
    onDisconnect(ctx, e)
  }
}

