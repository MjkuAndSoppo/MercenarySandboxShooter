package com.mercenarysandbox.msb.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import com.mercenarysandbox.msb.entity.AiCombatantEntity;

/**
 * AI 战斗单位模型：细臂 PlayerModel + 交战抬枪瞄准姿势。
 * 交战（isAiming，服务端同步）时双臂抬至水平并跟随视线方向
 * （取值同原版骷髅射手 BOW_AND_ARROW / 弩持握姿势，观感即「抬手瞄准」）。
 * 直接改手臂旋转（不依赖 ArmPose 的应用时机），改后需重复制袖子层，否则细臂袖子错位。
 */
public class AiCombatantModel extends PlayerModel<AiCombatantEntity> {
    /** 抬臂基准角 -90°：手臂由垂下改为水平前伸（原版 BOW_AND_ARROW 同源数值） */
    private static final float ARM_RAISE = -1.5707964F;

    public AiCombatantModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(AiCombatantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!entity.isAiming()) {
            return;
        }
        // 双臂端平指向视线方向（head 的 yaw/pitch 已在 super 中算好）
        this.rightArm.xRot = this.head.xRot + ARM_RAISE;
        this.rightArm.yRot = this.head.yRot;
        this.leftArm.xRot = this.head.xRot + ARM_RAISE;
        this.leftArm.yRot = this.head.yRot;
        // 袖子跟随手臂（PlayerModel 的袖子在 super 里已复制过一次，此处需再同步）
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
    }
}