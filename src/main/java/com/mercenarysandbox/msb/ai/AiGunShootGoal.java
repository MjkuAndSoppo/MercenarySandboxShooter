package com.mercenarysandbox.msb.ai;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.mob_guns.MobGunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.mercenarysandbox.msb.entity.AiCombatantEntity;
import com.mercenarysandbox.msb.faction.FactionManager;

/**
 * AI 枪械射击 goal（自研；仅调用 SBW 公开 API：GunData.tick/canShoot/shoot/startReload/startBolt，
 * 不复制 SBW 代码），替代 SBW 的 GunShootGoal 以实现两段距离分档交战：
 * - 40 格内锁定（索敌范围由实体 FOLLOW_RANGE 决定），有视线即开火压制
 * - 距离 &gt; 30 格：一边逼近一边压制，1 秒 1 发
 * - 距离 ≤ 30 格：停下扫射，按武器 RPM 全速开火
 * 另有：换弹时全速后撤拉开距离、换弹音效补播（SBW 仅对玩家播）、射击夹角内有友方单位时停火避让。
 * 瞄准参数（AimTime / Spread / Zoom）仍读 mob_guns JSON（data 即 MobGunData）。
 */
public class AiGunShootGoal extends Goal {
    /** 站定扫射距离（格）：&gt;30 格边逼近边压制，≤30 格停下扫射 */
    private static final double STATION_DISTANCE = 30.0D;
    /** 逼近段压制射速：20 tick = 1 秒 1 发 */
    private static final int SUPPRESS_INTERVAL_TICKS = 20;
    /** 首发射击的计时哨兵（避免 Long.MIN_VALUE 相减溢出导致永不开火） */
    private static final long FIRST_SHOT_READY = -100000L;
    /** 射击夹角判定：射线到友方单位的横向安全距离（格），小于该值视为挡线 */
    private static final double FRIENDLY_LINE_CLEARANCE = 1.5D;
    /** 与挡线队友拉开到该距离（格）以上后恢复开火 */
    private static final double FRIENDLY_CLEAR_DISTANCE = 5.0D;
    /** 后撤移动的目标距离（格） */
    private static final double RETREAT_DISTANCE = 8.0D;

    private final AiCombatantEntity mob;
    private final MobGunData data;
    private int aimTime;
    private long lastShotTick = FIRST_SHOT_READY;

    public AiGunShootGoal(AiCombatantEntity mob, MobGunData data) {
        this.mob = mob;
        this.data = data;
    }

    @Override
    public boolean canUse() {
        // 必须先调 getGunData()：它负责 mob_guns 选枪初始化（selectedData），
        // 不初始化则后续 aimTime()/spread()/zoom() 读 selectedData 会空指针（SBW 原 goal 亦在 canUse 中初始化）
        return this.mob.getTarget() != null
                && this.data.getGunData() != null
                && hasAmmo();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    /** 主手为 SBW 枪械且仍有弹药（弹匣/虚拟弹药任一可用） */
    private boolean hasAmmo() {
        ItemStack stack = this.mob.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) {
            return false;
        }
        GunData gunData = GunData.from(stack);
        return gunData.countBackupAmmo(this.mob) > 0 || gunData.hasEnoughAmmoToShoot(this.mob);
    }

    @Override
    public void start() {
        // 举枪姿态（SBW 亦如此，影响手臂渲染朝向）
        this.mob.setAggressive(true);
        // 同步瞄准状态：客户端渲染抬枪瞄准姿势（骷髅射手同款）
        this.mob.setAiming(true);
    }

    @Override
    public void stop() {
        this.mob.setAggressive(false);
        this.mob.setAiming(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        // 真实欧氏距离（SBW 原 goal 误用平方距离比较，此处按正确距离分档）
        double distance = Math.sqrt(this.mob.distanceToSqr(target));
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        if (canSee) {
            this.aimTime = Math.min(this.data.aimTime(), this.aimTime + 1);
        } else {
            // 丢失视线即重置瞄准进度（压制段无遮挡要求，靠 spread 控制命中）
            this.aimTime = 0;
        }
        this.mob.lookAt(target, 30.0F, 30.0F);

        ItemStack stack = this.mob.getMainHandItem();
        GunData gunData = GunData.from(stack);
        gunData.tick(this.mob, true);
        if (gunData.shouldStartReloading(this.mob)) {
            gunData.startReload();
        }
        if (gunData.shouldStartBolt()) {
            gunData.startBolt();
        }

        // 换弹真空期：全速与目标拉开距离（不参与站定/逼近逻辑）
        if (gunData.reloading()) {
            retreatFrom(target, 1.5D);
            return;
        }

        if (distance > STATION_DISTANCE) {
            this.mob.getNavigation().moveTo(target, 1.0D);
        } else {
            this.mob.getNavigation().stop();
        }

        if (!canSee) {
            return;
        }

        if (this.aimTime >= this.data.aimTime() && gunData.canShoot(this.mob)) {
            long now = this.mob.level().getGameTime();
            if (now - this.lastShotTick >= shotInterval(distance, gunData)) {
                this.lastShotTick = now;
                // 射击夹角内有同阵营单位：停火并后撤避让（拉开 5 格以上后自然恢复）
                LivingEntity blocker = friendlyBlocker(target);
                if (blocker != null) {
                    if (this.mob.distanceTo(blocker) < FRIENDLY_CLEAR_DISTANCE) {
                        retreatFrom(blocker, 1.2D);
                    }
                    return;
                }
                gunData.shoot(this.mob, this.data.spread(), this.data.zoom(), target.getUUID());
            }
        }
    }

    /** 分档射速（tick）：&gt;30 格 1 秒 1 发压制；≤30 格按武器 RPM（1200/RPM，最少 1 tick） */
    private static int shotInterval(double distance, GunData gunData) {
        if (distance > STATION_DISTANCE) {
            return SUPPRESS_INTERVAL_TICKS;
        }
        int rpm = gunData.get(GunProp.RPM);
        if (rpm <= 0) {
            return SUPPRESS_INTERVAL_TICKS;
        }
        return Math.max(1, Math.round(1200.0F / rpm));
    }

    /** 射击射线（mob 眼位 → 目标躯干）夹角内最近的同阵营单位；无挡线返回 null */
    private LivingEntity friendlyBlocker(LivingEntity target) {
        Vec3 from = new Vec3(this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 0.5D) {
            return null;
        }
        Vec3 unit = dir.scale(1.0D / dist);
        List<LivingEntity> near = this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(dist),
                e -> e != this.mob && e != target && isFriendly(e));
        LivingEntity best = null;
        double bestAlong = Double.MAX_VALUE;
        for (LivingEntity e : near) {
            Vec3 p = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ());
            Vec3 rel = p.subtract(from);
            double along = rel.dot(unit);
            if (along <= 0.0D || along >= dist) {
                continue;
            }
            double lateral = rel.subtract(unit.scale(along)).length();
            if (lateral <= FRIENDLY_LINE_CLEARANCE && along < bestAlong) {
                bestAlong = along;
                best = e;
            }
        }
        return best;
    }

    /** 同阵营判定：战斗 AI 取实体阵营，真人玩家取分配阵营 */
    private boolean isFriendly(LivingEntity e) {
        if (e instanceof AiCombatantEntity ai) {
            return ai.getFaction() == this.mob.getFaction();
        }
        if (e instanceof ServerPlayer player) {
            return FactionManager.getPlayerFaction(player) == this.mob.getFaction();
        }
        return false;
    }

    /** 向远离 other 的方向移动 RETREAT_DISTANCE 格（换弹后撤 / 避让挡线队友） */
    private void retreatFrom(LivingEntity other, double speed) {
        double ax = this.mob.getX() - other.getX();
        double az = this.mob.getZ() - other.getZ();
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 1.0E-4D) {
            // 与目标重叠时取随机方向，避免除零
            ax = this.mob.getRandom().nextDouble() - 0.5D;
            az = this.mob.getRandom().nextDouble() - 0.5D;
            len = Math.sqrt(ax * ax + az * az);
        }
        this.mob.getNavigation().moveTo(this.mob.getX() + ax / len * RETREAT_DISTANCE,
                this.mob.getY(), this.mob.getZ() + az / len * RETREAT_DISTANCE, speed);
    }
}