package com.mercenarysandbox.msb.data;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import com.mercenarysandbox.msb.MercenarySandboxShooter;

/**
 * 方块模型/blockstate 生成（docs/02 §8：资源一律走 datagen）。
 * 基地方块复用原版羊毛纹理（零新增贴图资源），三阵营用红/蓝/绿羊毛区分。
 */
public final class MsbBlockStates extends BlockStateProvider {
    public MsbBlockStates(PackOutput output, String modid, ExistingFileHelper existingFileHelper) {
        super(output, modid, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile lonestar = models().cubeAll("base_block_lonestar", mcLoc("block/red_wool"));
        ModelFile valkyra = models().cubeAll("base_block_valkyra", mcLoc("block/blue_wool"));
        ModelFile manticore = models().cubeAll("base_block_manticore", mcLoc("block/green_wool"));

        simpleBlock(MercenarySandboxShooter.BASE_BLOCK_LONESTAR.get(), lonestar);
        simpleBlock(MercenarySandboxShooter.BASE_BLOCK_VALKYRA.get(), valkyra);
        simpleBlock(MercenarySandboxShooter.BASE_BLOCK_MANTICORE.get(), manticore);

        simpleBlockItem(MercenarySandboxShooter.BASE_BLOCK_LONESTAR.get(), lonestar);
        simpleBlockItem(MercenarySandboxShooter.BASE_BLOCK_VALKYRA.get(), valkyra);
        simpleBlockItem(MercenarySandboxShooter.BASE_BLOCK_MANTICORE.get(), manticore);
    }
}