package com.mercenarysandbox.msb.entity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.mob_guns.MobGunData;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.mercenarysandbox.msb.Config;

import com.mercenarysandbox.msb.ai.AiGunShootGoal;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;

/**
 * AI 战斗单位实体（M1 实体化，docs/02 §3.12；M2 战斗逻辑）。
 * 原版 Steve 外观（PlayerModel + 阵营 PMC 皮肤，slim 细臂），主手装配 SBW 枪械，
 * 服务端目标驱动：发现敌方（真人玩家 + 敌方 AI）→ GunShootGoal 瞄准开火（SBW 抛射物）。
 * 每个 AI 携带双容器：装备容器（初始武器/弹药，掉落受 /MSBS AIpmc drop 控制）
 * 与战利品容器（主动拾取的掉落物，死亡始终掉落供玩家搜刮）。
 * 阵营以 SynchedEntityData 同步（渲染器按阵营切换皮肤）+ NBT 持久化（服务端跨存档）。
 */
public class AiCombatantEntity extends PathfinderMob implements Container {
    /** 阵营同步键：普通字段不跨客户端同步（渲染器按阵营切皮肤），用实体数据流 */
    private static final EntityDataAccessor<Integer> DATA_FACTION_ID = SynchedEntityData.defineId(AiCombatantEntity.class, EntityDataSerializers.INT);
    /** 交战瞄准同步键：客户端渲染抬枪姿势（服务端战斗 goal 运行时置位） */
    private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(AiCombatantEntity.class, EntityDataSerializers.BOOLEAN);
    /** AI 双容器容量：装备容器（初始武器/弹药，掉落受 /MSBS AIpmc drop 控制）与战利品容器（拾取物，始终掉落） */
    private static final int CONTAINER_SIZE = 27;
    /** 枪内虚拟弹药（SBW mob 射击用：非玩家实体无物品栏，靠枪 NBT 供弹，docs/02 §3.12） */
    private static final int VIRTUAL_AMMO = 512;
    /** 敌方阵营枪械预设（映射 SBW 公开注册名，仅引用不复制代码；阵营轮换预设留待预设表） */
    private static final String GUN_ID_LONESTAR = "superbwarfare:hk_416";
    private static final String GUN_ID_MANTICORE = "superbwarfare:m_4";
    private static final String GUN_ID_VALKYRA = "superbwarfare:ak_12";
    private static final String GUN_ID_SIDEARM = "superbwarfare:glock_17";
    private static final String GUN_ID_FALLBACK = "superbwarfare:mp_5";
    /** 容器内弹药物品（SBW 弹药物品注册名，与枪械 AmmoType 对应：手枪=glock_17、步枪=三队主武器） */
    private static final String AMMO_ID_HANDGUN = "superbwarfare:handgun_ammo";
    private static final String AMMO_ID_RIFLE = "superbwarfare:rifle_ammo";
    /** 受击后通知反击的队友半径（格） */
    private static final double ALLY_ALERT_RADIUS = 8.0D;
    /** 非交战状态主动拾取的掉落物扫描半径（格） */
    private static final double PICKUP_RADIUS = 5.0D;
    /** 走到掉落物该距离（格）内即收取 */
    private static final double PICKUP_STOP_DISTANCE = 1.5D;

    /** 装备容器：初始副武器 + 弹药（掉落受 /MSBS AIpmc drop 控制） */
    private final SimpleContainer equipment = new SimpleContainer(CONTAINER_SIZE);
    /** 战利品容器：拾取的掉落物（死亡始终掉落，与装备掉落开关无关） */
    private final SimpleContainer loot = new SimpleContainer(CONTAINER_SIZE);
    /** 圈内巡逻换点倒计时（lazyTick 调用次数；每 4 tick 一次调用，60 ≈ 12s） */
    private int patrolCooldown;

    public AiCombatantEntity(EntityType<? extends AiCombatantEntity> type, Level level) {
        super(type, level);
        // 永久存在：不自然消失，生命周期由 AiManager 顶替/补位/击杀清理管理
        setPersistenceRequired();
        if (!level.isClientSide) {
            registerCombatGoals();
        }
    }

    /** 服务端战斗 goal：目标选择（敌方真人 + 敌方 AI）+ 自研分档射击（mob_guns 数据缺失时退化为只索敌不开火） */
    private void registerCombatGoals() {
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, this::isEnemy));
        MobGunData gunData = MobGunData.from(this);
        if (gunData != null) {
            this.goalSelector.addGoal(1, new AiGunShootGoal(this, gunData));
        }
    }

    /** 敌我识别（服务端权威，docs/02 §3.1）：敌方阵营真人玩家、敌方 AI、非中立生物（敌对怪物）为目标 */
    private boolean isEnemy(LivingEntity target) {
        if (target == this || target.isDeadOrDying()) {
            return false;
        }
        // 非中立生物（僵尸/骷髅/灾厄等 Enemy 接口实现）一律主动攻击
        if (target instanceof Enemy) {
            return true;
        }
        if (target instanceof AiCombatantEntity ai) {
            Faction f = ai.getFaction();
            return f != Faction.NONE && f != getFaction();
        }
        if (target instanceof ServerPlayer player) {
            Faction f = FactionManager.getPlayerFaction(player);
            return f != Faction.NONE && f != getFaction();
        }
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACTION_ID, Faction.NONE.getId());
        builder.define(DATA_AIMING, false);
    }

    /** 是否处于交战瞄准状态（渲染抬枪姿势用；服务端写入、客户端读取） */
    public boolean isAiming() {
        return this.entityData.get(DATA_AIMING);
    }

    public void setAiming(boolean aiming) {
        this.entityData.set(DATA_AIMING, aiming);
    }

    public Faction getFaction() {
        return Faction.byId(entityData.get(DATA_FACTION_ID));
    }

    /** 实体属性（供 DefaultAttributes.register 在实体类型注册后绑定，LivingEntity 构造期需要）
     * FOLLOW_RANGE 控制索敌距离（战斗目标选择范围） */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public void setFaction(Faction faction) {
        // 服务端权威写入实体数据流，自动同步到客户端（渲染器按阵营切皮肤）
        this.entityData.set(DATA_FACTION_ID, faction.getId());
    }

    /** 计分板 Team 成员名：用 UUID（与真人玩家名区分，供 addPlayerToTeam/removePlayerFromTeam） */
    @Override
    public String getScoreboardName() {
        return getStringUUID();
    }

    /**
     * 出生装配（AiManager.spawnUnit 调用）：主手 SBW 枪械 + 枪内虚拟弹药（mob 射击供弹），
     * 装备容器放副武器（带弹）+ 弹药（掉落受 /MSBS AIpmc drop 控制；主武器只在主手、避免重复掉落）。
     */
    public void equipLoadout() {
        String gunId = switch (getFaction()) {
            case LONESTAR -> GUN_ID_LONESTAR;
            case MANTICORE -> GUN_ID_MANTICORE;
            case VALKYRA -> GUN_ID_VALKYRA;
            default -> GUN_ID_FALLBACK;
        };
        setItemInHand(InteractionHand.MAIN_HAND, createGun(gunId));
        equipment.setItem(0, createGun(GUN_ID_SIDEARM));
        equipment.setItem(1, createAmmo(AMMO_ID_HANDGUN, 32));
        equipment.setItem(2, createAmmo(AMMO_ID_RIFLE, 64));
    }

    /** 生成一叠弹药物品（SBW AmmoSupplierItem，玩家搜刮后可直接装填） */
    private static ItemStack createAmmo(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count);
    }

    /** 生成一把带满弹匣 + 虚拟弹药兜底的 SBW 枪械（防弹药计数为 0 导致无法开火） */
    private static ItemStack createGun(String id) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        if (stack.getItem() instanceof GunItem) {
            try {
                GunData data = GunData.from(stack);
                if (data != null) {
                    data.virtualAmmo.set(VIRTUAL_AMMO);
                    int mag = data.get(GunProp.MAGAZINE);
                    data.ammo.set(Math.max(1, mag));
                    // 手动写回 CUSTOM_DATA：GunData.save() 的 fast-path 靠 nbtVersion 门控，
                    // 而 SBW 内部从不调用 invalidateState()，弹药这类变更走 save() 不会落盘；
                    // 且 DATA_CACHE 是 weakKeys+weakValues，缓存一旦失效会从 stack NBT 重读——
                    // 不写回则 AI 枪械弹药变 0、无法开火（存档往返同样丢失）
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data.tag()));
                }
            } catch (Exception ignored) {
                // SBW 枪械数据加载异常时保留空枪，不阻塞 AI 生成
            }
        }
        return stack;
    }

    /**
     * 惰性推进（docs/02 §3.12 低频决策约束）：由 AiManager 每 4 tick 调用一次。
     * 无攻击目标 → 圈外朝圆心移动、圈内停止；有目标 → 交给战斗 goal（GunShootGoal 负责逼近/开火）。
     */
    public void lazyTick(BlockPos zoneCenter, int radius) {
        if (isRemoved() || isDeadOrDying() || getTarget() != null) {
            return;
        }
        // 非交战：扫描 5 格内掉落物并主动走过去拾取（优先于巡逻/推进）
        if (chaseAndPickUpLoot()) {
            return;
        }
        double dx = zoneCenter.getX() + 0.5D - getX();
        double dz = zoneCenter.getZ() + 0.5D - getZ();
        boolean inZone = dx * dx + dz * dz <= (double) radius * radius;
        if (!inZone) {
            // 圈外：朝控制区推进
            getNavigation().moveTo(zoneCenter.getX() + 0.5D, zoneCenter.getY(), zoneCenter.getZ() + 0.5D, 0.8D);
            return;
        }
        // 圈内自由巡逻：到达当前目标点后隔一段时间随机换一个圈内点漫游
        if (!getNavigation().isDone()) {
            return;
        }
        if (patrolCooldown > 0) {
            patrolCooldown--;
            return;
        }
        patrolCooldown = 60 + getRandom().nextInt(60);
        double angle = getRandom().nextDouble() * Math.PI * 2.0D;
        double dist = getRandom().nextDouble() * radius * 0.8D;
        double tx = zoneCenter.getX() + 0.5D + Math.cos(angle) * dist;
        double tz = zoneCenter.getZ() + 0.5D + Math.sin(angle) * dist;
        getNavigation().moveTo(tx, zoneCenter.getY(), tz, 0.6D);
    }

    /**
     * 非交战状态：扫描 5 格内掉落物并主动移动过去拾取（走到物品旁即收进战利品区）。
     * @return true 表示正在处理掉落物（本次不巡逻/不推进）
     */
    private boolean chaseAndPickUpLoot() {
        if (level().isClientSide) {
            return false;
        }
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(PICKUP_RADIUS));
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            if (item.isRemoved() || item.hasPickUpDelay() || item.getItem().isEmpty()) {
                continue;
            }
            double d = distanceToSqr(item);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        if (nearest == null) {
            return false;
        }
        if (best > PICKUP_STOP_DISTANCE * PICKUP_STOP_DISTANCE) {
            // 主动移动到掉落物旁
            getNavigation().moveTo(nearest, 1.0D);
        } else {
            // 已到物品旁：收进战利品容器（27 格全部可用）
            ItemStack remaining = loot.addItem(nearest.getItem().copy());
            if (remaining.isEmpty()) {
                nearest.discard();
            } else {
                nearest.setItem(remaining);
            }
        }
        return true;
    }

    /**
     * 被攻击后反击（服务端伤害事件调用）：立刻锁定攻击者，并通知半径 8 格内同阵营 AI 队友一起索敌。
     * 同阵营误伤不触发（isEnemy 已排除同阵营）。
     */
    public void retaliate(LivingEntity attacker) {
        if (level().isClientSide || !isEnemy(attacker)) {
            return;
        }
        setLastHurtByMob(attacker);
        if (getTarget() == null) {
            setTarget(attacker);
        }
        List<AiCombatantEntity> allies = level().getEntitiesOfClass(AiCombatantEntity.class,
                getBoundingBox().inflate(ALLY_ALERT_RADIUS), ally -> ally != this && ally.getFaction() == getFaction());
        for (AiCombatantEntity ally : allies) {
            if (ally.getTarget() == null) {
                ally.setTarget(attacker);
            }
        }
    }

    /**
     * 死亡掉落：主手武器只掉一把（先摘主手再走原版流程，防原版装备掉落叠加）。
     * 战利品容器（拾取物）始终掉落；装备容器（副武器/弹药）与主手武器由 /MSBS AIpmc drop 控制。
     */
    @Override
    public void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        ItemStack mainHand = getMainHandItem();
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        super.dropAllDeathLoot(level, source);
        boolean dropEquipment = Config.AI_DROP_LOOT.get();
        // 装备容器：受开关控制（关闭时销毁）
        if (dropEquipment) {
            for (int i = 0; i < equipment.getContainerSize(); i++) {
                ItemStack stack = equipment.getItem(i);
                if (!stack.isEmpty()) {
                    spawnAtLocation(stack);
                }
            }
        }
        // 战利品容器：始终掉落
        for (int i = 0; i < loot.getContainerSize(); i++) {
            ItemStack stack = loot.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        equipment.clearContent();
        loot.clearContent();
        if (dropEquipment && !mainHand.isEmpty()) {
            spawnAtLocation(mainHand);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Faction", getFaction().name());
        // 双容器分别存入子标签（ContainerHelper 默认用根 "Items" 键，避免两容器互相覆盖）
        CompoundTag equipmentTag = new CompoundTag();
        ContainerHelper.saveAllItems(equipmentTag, equipment.getItems(), this.registryAccess());
        tag.put("Equipment", equipmentTag);
        CompoundTag lootTag = new CompoundTag();
        ContainerHelper.saveAllItems(lootTag, loot.getItems(), this.registryAccess());
        tag.put("Loot", lootTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Faction", CompoundTag.TAG_STRING)) {
            try {
                setFaction(Faction.valueOf(tag.getString("Faction")));
            } catch (IllegalArgumentException e) {
                setFaction(Faction.NONE);
            }
        }
        if (tag.contains("Equipment", CompoundTag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound("Equipment"), equipment.getItems(), this.registryAccess());
        }
        if (tag.contains("Loot", CompoundTag.TAG_COMPOUND)) {
            ContainerHelper.loadAllItems(tag.getCompound("Loot"), loot.getItems(), this.registryAccess());
        }
    }

    /** AI 不应被玩家用栓绳带走 */
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // ===== Container 接口（战利品容器）：委托 SimpleContainer =====
    @Override
    public int getContainerSize() {
        return loot.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return loot.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return loot.getItem(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return loot.removeItem(index, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return loot.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        loot.setItem(index, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return loot.stillValid(player);
    }

    @Override
    public void setChanged() {
        loot.setChanged();
    }

    @Override
    public void clearContent() {
        loot.clearContent();
    }
}
