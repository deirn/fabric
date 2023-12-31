package net.fabricmc.fabric.api.item.v1;

import com.mojang.serialization.Codec;

import net.minecraft.util.Identifier;

import net.fabricmc.fabric.impl.item.CustomItemPredicateRegistryImpl;

public final class CustomItemPredicateRegistry {
	public static void register(Identifier id, Codec<? extends CustomItemPredicate> codec) {
		CustomItemPredicateRegistryImpl.register(id, codec);
	}

	private CustomItemPredicateRegistry() {
	}
}
