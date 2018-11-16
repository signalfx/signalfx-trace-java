// Modified by SignalFx
package datadog.trace.instrumentation.netty41.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.AttributeKeys;
import datadog.trace.instrumentation.utils.URLUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.InetSocketAddress;
import java.util.Collections;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    if (!(msg instanceof HttpRequest)) {
      ctx.fireChannelRead(msg); // superclass does not throw
      return;
    }
    final HttpRequest request = (HttpRequest) msg;
    final InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();

    final SpanContext extractedContext =
        GlobalTracer.get()
            .extract(Format.Builtin.HTTP_HEADERS, new NettyRequestExtractAdapter(request));

    String url = request.uri();
    if (request.headers().contains(HOST)) {
      url = "http://" + request.headers().get(HOST) + url;
    }
    final Scope scope =
        GlobalTracer.get()
            .buildSpan(URLUtil.deriveOperationName(request.method().name(), url, false))
            .asChildOf(extractedContext)
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(Tags.PEER_HOSTNAME.getKey(), remoteAddress.getHostName())
            .withTag(Tags.PEER_PORT.getKey(), remoteAddress.getPort())
            .withTag(Tags.HTTP_METHOD.getKey(), request.method().name())
            .withTag(Tags.HTTP_URL.getKey(), url)
            .withTag(Tags.COMPONENT.getKey(), "netty")
            .startActive(false);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    final Span span = scope.span();
    ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).set(span);

    try {
      ctx.fireChannelRead(msg);
    } catch (final Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    } finally {
      scope.close();
    }
  }
}
