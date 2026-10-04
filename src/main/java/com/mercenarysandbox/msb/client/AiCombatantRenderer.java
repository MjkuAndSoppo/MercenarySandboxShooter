package com.mercenarysandbox.msb.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.Faction;

/**
 * AI 战斗单位渲染器（M1 实体化）：阵营 PMC 皮肤 + slim（细臂）PlayerModel，按阵营切换纹理。
 * 阵营染色由计分板 Team（Entity.getTeam()）自动作用于名字标签；
 * 交战抬枪姿势由 AiCombatantModel 按同步的 isAiming 状态驱动。
 */
public class AiCombatantRenderer extends MobRenderer<AiCombatantEntity, AiCombatantModel> {
    /** 阵营 PMC 皮肤（红 S.F / 绿 I.O.P / 蓝 KCCO） */
    private static final ResourceLocation SF_TEXTURE = ResourceLocation.fromNamespaceAndPath("msb", "textures/entity/sf_pmc.png");
    private static final ResourceLocation IOP_TEXTURE = ResourceLocation.fromNamespaceAndPath("msb", "textures/entity/iop_pmc.png");
    private static final ResourceLocation KCCO_TEXTURE = ResourceLocation.fromNamespaceAndPath("msb", "textures/entity/ksso_pmc.png");

    public AiCombatantRenderer(EntityRendererProvider.Context context) {
        super(context, new AiCombatantModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
        // 主手渲染 SBW 枪械（AI 装配的战斗武器，装备层挂载在 PlayerModel 右手）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        // 开伞伞面（服务端同步 isParachuteOpen 后绘制，复用 SBW 的 ParachuteModel 与伞纹理）
        this.addLayer(new AiParachuteLayer(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(AiCombatantEntity entity) {
        return switch (entity.getFaction()) {
            case LONESTAR -> SF_TEXTURE;
            case MANTICORE -> IOP_TEXTURE;
            case VALKYRA -> KCCO_TEXTURE;
            // 兜底用 msb 自产纹理（原版 steve.png 在部分环境资源加载失败会紫黑）
            case NONE -> SF_TEXTURE;
        };
    }

    /**
     * 与 PlayerRenderer 保持一致：人形模型整体缩放 0.9375。
     * PlayerModel 原始高度约 2.0 格，原版渲染器靠该系数把模型压到 0.6×1.8 碰撞盒尺寸上；
     * MobRenderer 默认不缩放，若不补这一步，AI 模型会比自身碰撞盒大约 6.7%（盒相对躯体显得偏小）。
     */
    @Override
    protected void scale(AiCombatantEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
