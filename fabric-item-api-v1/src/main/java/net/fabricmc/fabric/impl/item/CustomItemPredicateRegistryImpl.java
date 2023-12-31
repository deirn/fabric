package net.fabricmc.fabric.impl.item;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;

import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.item.v1.CustomItemPredicate;

public class CustomItemPredicateRegistryImpl {
	public static final BiMap<String, Codec<CustomItemPredicate>> MAP = HashBiMap.create();

	@SuppressWarnings("unchecked")
	public static void register(Identifier id, Codec<? extends CustomItemPredicate> codec) {
		MAP.put(id.toString(), (Codec<CustomItemPredicate>) codec);
	}
}
