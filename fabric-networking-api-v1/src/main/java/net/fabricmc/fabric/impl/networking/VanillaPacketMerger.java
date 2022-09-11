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

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/**
 * This class handles merging of split vanilla packets and combining it into a whole.
 */
public class VanillaPacketMerger {
	private static final Logger LOGGER = LoggerFactory.getLogger(VanillaPacketMerger.class);

	private final ClientConnection connection;

	@Nullable
	private Current current;

	public VanillaPacketMerger(ClientConnection connection) {
		this.connection = connection;
	}

	/**
	 * @see ClientConnection#channelRead0(ChannelHandlerContext, Packet)
	 */
	@SuppressWarnings({"unchecked", "JavadocReference"})
	public void handle(PacketByteBuf buf) {
		int packetId = buf.readVarInt();
		boolean end = buf.readBoolean();

		if (current == null) {
			current = new Current(packetId, PacketByteBufs.create());
		}

		current.buf.writeBytes(buf);

		if (current.id != packetId) {
			throw new IllegalStateException("Packet out of order! Received part for packet id " + packetId + " while handling " + current.id);
		}

		if (end) {
			// Needs to be set to null before calling apply otherwise it won't get set, threading issue?
			Current finished = current;
			current = null;
			Packet<PacketListener> packet = (Packet<PacketListener>) NetworkState.PLAY.getPacketHandler(connection.getSide(), finished.id, finished.buf);
			Objects.requireNonNull(packet, () -> "Unknown packet with id " + finished.id);

			try {
				packet.apply(connection.getPacketListener());
			} catch (OffThreadException ignored) {
				// no-op
			} catch (RejectedExecutionException rejectedExecutionException) {
				connection.disconnect(Text.translatable("multiplayer.disconnect.server_shutdown"));
			} catch (ClassCastException classCastException) {
				LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classCastException);
				connection.disconnect(Text.translatable("multiplayer.disconnect.invalid_packet"));
			}
		}
	}

	public interface Holder {
		VanillaPacketMerger fabric_getVanillaPacketMerger();
	}

	private record Current(int id, PacketByteBuf buf) {
	}
}
