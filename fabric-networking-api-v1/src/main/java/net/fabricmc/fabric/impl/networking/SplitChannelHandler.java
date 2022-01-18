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

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.PacketByteBuf;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class SplitChannelHandler {
	@Nullable
	private PacketByteBuf combinedBuf;

	protected void receive(PacketByteBuf slicedBuf, Consumer<PacketByteBuf> combinedBufConsumer) {
		if (combinedBuf == null) {
			combinedBuf = PacketByteBufs.create();
		}

		if (slicedBuf.readableBytes() > 0) {
			combinedBuf.writeBytes(slicedBuf);
			return;
		}

		combinedBufConsumer.accept(combinedBuf);
		combinedBuf.release();
		combinedBuf = null;
	}
}
