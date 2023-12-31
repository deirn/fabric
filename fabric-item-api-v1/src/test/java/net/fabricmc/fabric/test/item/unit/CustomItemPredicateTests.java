package net.fabricmc.fabric.test.item.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.item.ItemPredicate;

import net.fabricmc.fabric.api.item.v1.CustomItemPredicate;
import net.fabricmc.fabric.test.item.CustomItemPredicateTest;
import net.fabricmc.fabric.test.item.CustomItemPredicateTest.NoOpItemPredicate;
import net.fabricmc.fabric.test.item.CustomItemPredicateTest.TierItemPredicate;

public class CustomItemPredicateTests {
	@BeforeAll
	static void beforeAll() {
		new CustomItemPredicateTest().onInitialize();
	}

	@Test
	void customPredicateCodec() {
		ItemPredicate authoritative = ItemPredicate.Builder.create()
				.custom(new TierItemPredicate(3))
				.custom(NoOpItemPredicate.INSTANCE_1)
				.custom(NoOpItemPredicate.INSTANCE_2)
				.build();

		DataResult<JsonElement> jsonResult = ItemPredicate.CODEC.encodeStart(JsonOps.INSTANCE, authoritative);
		assertTrue(jsonResult.result().isPresent());
		JsonElement json = jsonResult.result().orElseThrow();

		DataResult<ItemPredicate> reserializedResult = ItemPredicate.CODEC.decode(JsonOps.INSTANCE, json).map(Pair::getFirst);
		assertTrue(reserializedResult.result().isPresent());
		ItemPredicate reserialized = reserializedResult.result().orElseThrow();

		assertTrue(reserialized.tag().isEmpty());
		assertTrue(reserialized.items().isEmpty());
		assertEquals(NumberRange.IntRange.ANY, reserialized.count());
		assertEquals(NumberRange.IntRange.ANY, reserialized.durability());
		assertTrue(reserialized.enchantments().isEmpty());
		assertTrue(reserialized.storedEnchantments().isEmpty());
		assertTrue(reserialized.potion().isEmpty());
		assertTrue(reserialized.nbt().isEmpty());

		List<CustomItemPredicate> custom = reserialized.custom();
		assertEquals(3, custom.size());
		assertInstanceOf(TierItemPredicate.class, custom.get(0));
		assertEquals(3, ((TierItemPredicate) custom.get(0)).tier());
		assertSame(NoOpItemPredicate.INSTANCE_1, custom.get(0));
		assertSame(NoOpItemPredicate.INSTANCE_2, custom.get(1));
	}
}
