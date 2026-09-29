package com.mercenarysandbox.msb.data;

import java.util.List;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 方块掉落表生成（基地方块掉落自身，docs/02 §8）。
 */
public final class MsbBlockLoot extends BlockLootSubProvider {
    public MsbBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, registries);
    }

    @Override
    protected void generate() {
        dropSelf(MercenarySandboxShooter.BASE_BLOCK_LONESTAR.get());
        dropSelf(MercenarySandboxShooter.BASE_BLOCK_VALKYRA.get());
        dropSelf(MercenarySandboxShooter.BASE_BLOCK_MANTICORE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return List.of(
                MercenarySandboxShooter.BASE_BLOCK_LONESTAR.get(),
                MercenarySandboxShooter.BASE_BLOCK_VALKYRA.get(),
                MercenarySandboxShooter.BASE_BLOCK_MANTICORE.get());
    }
}