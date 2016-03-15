package cc.blynk.utils;

import cc.blynk.server.core.protocol.enums.Command;
import cc.blynk.server.core.protocol.model.messages.MessageBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;

/**
 * Utility class that creates native netty buffers instead of java objects.
 * This is done in order to allocate less java objects and reduce GC pauses and load.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 10.03.16.
 */
public class ByteBufUtil {

    public static ByteBuf makeResponse(ChannelHandlerContext ctx, int msgId, int response) {
        return ctx.alloc().ioBuffer(MessageBase.HEADER_LENGTH)
                .writeByte(Command.RESPONSE)
                .writeShort(msgId)
                .writeShort(response);
    }

    public static ByteBuf makeStringMessage(ChannelHandlerContext ctx, short cmd, int msgId, String data) {
        return makeStringMessage(ctx.alloc(), cmd, msgId, data);
    }

    public static ByteBuf makeStringMessage(Channel channel, short cmd, int msgId, String data) {
        return makeStringMessage(channel.alloc(), cmd, msgId, data);
    }

    private static ByteBuf makeStringMessage(ByteBufAllocator allocator, short cmd, int msgId, String data) {
        return allocator.ioBuffer(MessageBase.HEADER_LENGTH + data.length())
                .writeByte(cmd)
                .writeShort(msgId)
                .writeShort(data.length())
                .writeBytes(data.getBytes(CharsetUtil.UTF_8));
    }

}