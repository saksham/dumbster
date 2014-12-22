package com.dumbster.smtp.transport;

import com.dumbster.smtp.api.ApiResponse;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;

public class ApiRequestEncoder extends MessageToMessageEncoder<ApiResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ApiResponse apiResponse, List<Object> out) throws Exception {
        String apiResponseAsString = apiResponse.marshalResponse();
        out.add(ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.wrap(apiResponseAsString), Charset.defaultCharset()));
    }
}
