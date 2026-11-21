package com.odmtitan;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class ModRegistry {
    public static final String MOD_ID = "odmtitan";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    // ---- per-player data: the dedicated ODM slot + the gas tank --------------------------------
    /** true = ODM gear is sitting in the player's dedicated ODM slot. */
    public static final AttachmentType<Boolean> GEAR_EQUIPPED = AttachmentRegistry.<Boolean>create(
            id("gear_equipped"), b -> b.persistent(Codec.BOOL).initializer(() -> false).copyOnDeath());
    /** Gas units left (0 .. OdmData.MAX_GAS). */
    public static final AttachmentType<Integer> GAS = AttachmentRegistry.<Integer>create(
            id("gas"), b -> b.persistent(Codec.INT).initializer(() -> OdmData.MAX_GAS).copyOnDeath());

    // ---- items ---------------------------------------------------------------------------------
    private static Item.Properties props(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)));
    }

    private static Item item(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), item);
    }

    public static final Item ODM_GEAR = item("odm_gear",
            new OdmGearItem(props("odm_gear").stacksTo(1).rarity(Rarity.EPIC)));

    public static final Item GAS_CANISTER = item("gas_canister",
            new GasCanisterItem(props("gas_canister").stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final Item ODM_BLADE = item("odm_blade", new Item(props("odm_blade")
            .stacksTo(1).rarity(Rarity.RARE)
            .attributes(ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .add(Attributes.ATTACK_SPEED,
                            new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -1.2, AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.MAINHAND)
                    .build())));

    public static final Item BEAST_HORN = item("beast_horn",
            new BeastHornItem(props("beast_horn").stacksTo(1).rarity(Rarity.EPIC)));

    // ---- entity --------------------------------------------------------------------------------
    public static final ResourceKey<EntityType<?>> BEAST_TITAN_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("beast_titan"));

    public static final EntityType<BeastTitanEntity> BEAST_TITAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, BEAST_TITAN_KEY,
            EntityType.Builder.<BeastTitanEntity>of(BeastTitanEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)          // base size; the SCALE attribute enlarges it
                    .eyeHeight(1.74F)
                    .clientTrackingRange(24)
                    .updateInterval(2)
                    .build(BEAST_TITAN_KEY));

    // ---- creative tab --------------------------------------------------------------------------
    public static final ResourceKey<CreativeModeTab> TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.odmtitan"))
                    .icon(() -> new ItemStack(ODM_GEAR))
                    .displayItems((params, out) -> {
                        out.accept(ODM_GEAR);
                        out.accept(GAS_CANISTER);
                        out.accept(ODM_BLADE);
                        out.accept(BEAST_HORN);
                    })
                    .build());

    private ModRegistry() {}

    /** Touching this method forces the static initialisers above to run. */
    public static void init() {}
}
