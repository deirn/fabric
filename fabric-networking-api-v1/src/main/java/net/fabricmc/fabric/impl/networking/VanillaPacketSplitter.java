/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.networking;

import java.util.List;
import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeTagsS2CPacket;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/**
 * Splits select vanilla packets into multiple custom packets if its size exceed the supported threshold.
 *
 * @see PacketEncoder
 */
public class VanillaPacketSplitter extends MessageToMessageEncoder<Packet<?>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(VanillaPacketSplitter.class);

	/**
	 * @see PacketEncoder
	 */
	private static final int MAX_PAYLOAD_SIZE = Integer.getInteger("fabric.packetSplitter.payloadSize", 0x800000);
	/**
	 * @see CustomPayloadS2CPacket
	 */
	private static final int MAX_S2C_PART_SIZE = Integer.getInteger("fabric.packetSplitter.s2cPartSize", 0x100000);
	/**
	 * @see CustomPayloadC2SPacket
	 */
	private static final int MAX_C2S_PART_SIZE = Integer.getInteger("fabric.packetSplitter.c2sPartSize", Short.MAX_VALUE);
	private static final boolean SPLIT_ON_LOCAL = Boolean.getBoolean("fabric.packetSplitter.splitOnLocal");

	public static final Map<Class<? extends Packet<?>>, Strategy> STRATEGIES = Map.of(
			SynchronizeRecipesS2CPacket.class, new Strategy(NetworkSide.CLIENTBOUND, true),
			SynchronizeTagsS2CPacket.class, new Strategy(NetworkSide.CLIENTBOUND, true));

	private final ClientConnection connection;

	public VanillaPacketSplitter(ClientConnection connection) {
		this.connection = connection;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, Packet<?> packet, List<Object> out) {
		if (!STRATEGIES.containsKey(packet.getClass())) {
			out.add(packet);
			return;
		}

		if (connection.isLocal() && !SPLIT_ON_LOCAL) {
			LOGGER.debug("Skipping splitting {} packet on local connection", packet.getClass().getSimpleName());
			out.add(packet);
			return;
		}

		if (ctx.channel().attr(ClientConnection.PROTOCOL_ATTRIBUTE_KEY).get() != NetworkState.PLAY) {
			LOGGER.debug("Skipping splitting {} packet on non play state", packet.getClass().getSimpleName());
			out.add(packet);
			return;
		}

		if (connection.getPacketListener() instanceof NetworkHandlerExtensions ext) {
			if (ext.getAddon() instanceof AbstractChanneledNetworkAddon<?> addon) {
				if (!addon.getSendableChannels().contains(NetworkingImpl.SPLIT_CHANNEL)) {
					LOGGER.debug("Skipping splitting {} packet because target can not receive them", packet.getClass().getSimpleName());
					out.add(packet);
					return;
				}
			}
		}

		Strategy strategy = STRATEGIES.get(packet.getClass());
		int maxPartSize = strategy.side == NetworkSide.CLIENTBOUND ? MAX_S2C_PART_SIZE : MAX_C2S_PART_SIZE;

		Integer packetId = NetworkState.PLAY.getPacketId(strategy.side, packet);

		if (packetId == null) {
			LOGGER.error("Unknown packet {}", packet.getClass().getSimpleName());
			out.add(packet);
			return;
		}

		if (strategy.cached && strategy.cachedSize != -1 && strategy.cachedSize <= MAX_PAYLOAD_SIZE) {
			LOGGER.debug("Skipping splitting {} packet because of cached size {} is smaller or equal than max {}", packet.getClass().getSimpleName(), strategy.cachedSize, MAX_PAYLOAD_SIZE);
			out.add(packet);
			return;
		}

		PacketByteBuf buf = PacketByteBufs.create();
		packet.write(buf);

		if (strategy.cached) {
			strategy.cachedSize = buf.readableBytes();
		}

		if (buf.readableBytes() > MAX_PAYLOAD_SIZE) {
			int totalPart = 0;
			int packetSize = buf.readableBytes();

			while (buf.isReadable()) {
				// varint(id), bool(end marker), byte[](part)
				PacketByteBuf partBuf = PacketByteBufs.create();
				partBuf.writeVarInt(packetId);
				int partSize = Math.min(buf.readableBytes(), maxPartSize - partBuf.readableBytes() - 1);
				partBuf.writeBoolean(partSize == buf.readableBytes());
				partBuf.writeBytes(buf, partSize);

				switch (strategy.side) {
				case CLIENTBOUND -> out.add(new CustomPayloadS2CPacket(NetworkingImpl.SPLIT_CHANNEL, partBuf));
				case SERVERBOUND -> out.add(new CustomPayloadC2SPacket(NetworkingImpl.SPLIT_CHANNEL, partBuf));
				}

				totalPart++;
			}

			LOGGER.debug("Split {} packet sized {} into {} with {} each", packet.getClass().getSimpleName(), packetSize, totalPart, maxPartSize);
		} else {
			LOGGER.debug("Skipping splitting {} packet because its size {} is smaller or equal that max {}", packet.getClass().getSimpleName(), buf.readableBytes(), MAX_PAYLOAD_SIZE);
			out.add(packet);
		}

		buf.release();
	}

	public static final class Strategy {
		private final NetworkSide side;
		private final boolean cached;

		private volatile int cachedSize = -1;

		/**
		 * @param side   The side of the packet.
		 * @param cached If cached, packet size will only be checked once in the first {@link #encode} call
		 *               and will be used to decide if splitting is needed in later calls.
		 *               This prevents unnecessary buffer creation when splitting is not needed.
		 *               This is useful when the data associated with the packet is only rarely changed.
		 *               Call {@link #invalidateCache()} to make next encode call re-check the packet size.
		 */
		private Strategy(NetworkSide side, boolean cached) {
			this.side = side;
			this.cached = cached;
		}

		public void invalidateCache() {
			cachedSize = -1;
		}
	}
}
