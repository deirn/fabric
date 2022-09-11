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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeTagsS2CPacket;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class NetworkingImpl {
	public static final String MOD_ID = "fabric-networking-api-v1";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/**
	 * Id of packet used to register supported channels.
	 */
	public static final Identifier REGISTER_CHANNEL = new Identifier("minecraft", "register");
	/**
	 * Id of packet used to unregister supported channels.
	 */
	public static final Identifier UNREGISTER_CHANNEL = new Identifier("minecraft", "unregister");
	/**
	 * Id of the packet used to declare all currently supported channels.
	 * Dynamic registration of supported channels is still allowed using {@link NetworkingImpl#REGISTER_CHANNEL} and {@link NetworkingImpl#UNREGISTER_CHANNEL}.
	 */
	public static final Identifier EARLY_REGISTRATION_CHANNEL = new Identifier(MOD_ID, "early_registration");

	public static final Identifier SPLIT_CHANNEL = new Identifier(MOD_ID, "split");

	public static void init() {
		// Login setup
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
			// Send early registration packet
			PacketByteBuf buf = PacketByteBufs.create();
			Collection<Identifier> channelsNames = ServerPlayNetworking.getGlobalReceivers();
			buf.writeVarInt(channelsNames.size());

			for (Identifier id : channelsNames) {
				buf.writeIdentifier(id);
			}

			sender.sendPacket(EARLY_REGISTRATION_CHANNEL, buf);
			NetworkingImpl.LOGGER.debug("Sent accepted channels to the client for \"{}\"", handler.getConnectionInfo());
		});

		ServerLoginNetworking.registerGlobalReceiver(EARLY_REGISTRATION_CHANNEL, (server, handler, understood, buf, synchronizer, sender) -> {
			if (!understood) {
				// The client is likely a vanilla client.
				return;
			}

			int n = buf.readVarInt();
			List<Identifier> ids = new ArrayList<>(n);

			for (int i = 0; i < n; i++) {
				ids.add(buf.readIdentifier());
			}

			((ChannelInfoHolder) handler.getConnection()).getPendingChannelsNames().addAll(ids);
			NetworkingImpl.LOGGER.debug("Received accepted channels from the client for \"{}\"", handler.getConnectionInfo());
		});

		ServerPlayNetworking.registerGlobalReceiver(SPLIT_CHANNEL, (server, player, handler, buf, responseSender) ->
				((VanillaPacketMerger.Holder) handler.getConnection()).fabric_getVanillaPacketMerger().handle(buf));

		ServerLifecycleEvents.SERVER_STARTING.register(server ->
				VanillaPacketSplitter.STRATEGIES.values().forEach(VanillaPacketSplitter.Strategy::invalidateCache));

		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resourceManager) -> {
			// Re-check packet size on datapack reload.
			VanillaPacketSplitter.STRATEGIES.get(SynchronizeRecipesS2CPacket.class).invalidateCache();
			VanillaPacketSplitter.STRATEGIES.get(SynchronizeTagsS2CPacket.class).invalidateCache();
		});
	}

	public static boolean isReservedPlayChannel(Identifier channelName) {
		return channelName.equals(REGISTER_CHANNEL) || channelName.equals(UNREGISTER_CHANNEL);
	}
}
