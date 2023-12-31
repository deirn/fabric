package net.fabricmc.fabric.impl.item;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.util.dynamic.RuntimeOps;

import net.fabricmc.fabric.api.item.v1.CustomItemPredicate;

public class CustomItemPredicateCodec extends MapCodec<ItemPredicate> {
	private final MapCodec<ItemPredicate> vanilla;
	private final Set<String> vanillaKeys;

	public CustomItemPredicateCodec(Codec<ItemPredicate> vanilla) {
		this.vanilla = ((MapCodecCodec<ItemPredicate>) vanilla).codec();
		this.vanillaKeys = this.vanilla.keys(RuntimeOps.INSTANCE).map(Object::toString).collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public <T> Stream<T> keys(DynamicOps<T> ops) {

		Stream<T> custom = CustomItemPredicateRegistryImpl.MAP.keySet().stream().map(ops::createString);
		return Stream.concat(vanilla.keys(ops), custom);
	}

	@Override
	public <T> DataResult<ItemPredicate> decode(DynamicOps<T> ops, MapLike<T> input) {
		return vanilla.decode(ops, input).flatMap(predicate -> {
			MutableObject<DataResult<ItemPredicate>> result = new MutableObject<>(DataResult.success(predicate));
			ImmutableList.Builder<CustomItemPredicate> customs = new ImmutableList.Builder<>();

			input.entries().forEach(entry -> {
				if (result.getValue().error().isPresent()) return;

				result.setValue(ops.getStringValue(entry.getFirst()).flatMap(key -> {
					if (vanillaKeys.contains(key)) return DataResult.success(predicate);

					@Nullable Codec<CustomItemPredicate> customCodec = CustomItemPredicateRegistryImpl.MAP.get(key);
					if (customCodec == null) return DataResult.error(() -> "Unknown custom predicate id " + key);

					return customCodec.decode(ops, entry.getSecond()).map(p -> {
						customs.add(p.getFirst());
						return predicate;
					});
				}));
			});

			return result.getValue().map(p -> {
				((ItemPredicateExtensions) (Object) p).fabric_setCustom(customs.build());
				return p;
			});
		});
	}

	@Override
	public <T> RecordBuilder<T> encode(ItemPredicate input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
		RecordBuilder<T> builder = vanilla.encode(input, ops, prefix);
		if (input.custom().isEmpty()) return builder;

		for (CustomItemPredicate custom : input.custom()) {
			Codec<CustomItemPredicate> customCodec = (Codec<CustomItemPredicate>) custom.getCodec();
			String customId = CustomItemPredicateRegistryImpl.MAP.inverse().get(customCodec);

			builder.add(customId, customCodec.encodeStart(ops, custom));
		}

		return builder;
	}
}
