package com.mercenarysandbox.msb.client;

import com.atsuishio.superbwarfare.client.model.curio.ParachuteModel;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * AI 开伞伞面渲染层（服务端同步 isParachuteOpen 后绘制）。
 *
 * <p>SBW 的伞面由 Curios 渲染器按物品渲染，而 Curios 只给 player 开槽位、AI 实体没有背槽，
 * 因此这里自带一层：模型直接烘焙 SBW 已注册的 ParachuteModel layer、运行时引用其伞纹理，
 * 变换（0.5 缩放 + Y 轴 1.25 位移）与渲染类型对齐 SBW 的 ParachuteRenderer，保证观感一致。
 */
public class AiParachuteLayer extends RenderLayer<AiCombatantEntity, AiCombatantModel> {
    /** SBW 伞面纹理（运行时引用其资源，不复制进本模组） */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/curio/parachute.png");
    /** 伞面整体缩放与抬升（与 SBW ParachuteRenderer 一致） */
    private static final float SCALE = 0.5F;
    private static final double LIFT = 1.25D;

    private final ParachuteModel model;

    public AiParachuteLayer(RenderLayerParent<AiCombatantEntity, AiCombatantModel> parent, EntityModelSet models) {
        super(parent);
        this.model = new ParachuteModel(models.bakeLayer(ParachuteModel.Companion.getLAYER_LOCATION()));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, AiCombatantEntity entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!entity.isParachuteOpen()) {
            return;
        }
        pose.pushPose();
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(0.0D, LIFT, 0.0D);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(TEXTURE));
        model.renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
    }
}