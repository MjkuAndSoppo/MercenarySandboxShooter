package com.mercenarysandbox.msb.entity;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.mob_guns.MobGunData;
import com.atsuishio.superbwarfare.entity.goal.GunShootGoal;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.mercenarysandbox.msb.MercenarySandboxShooter;
import com.mercenarysandbox.msb.faction.Faction;
import com.mercenarysandbox.msb.faction.FactionManager;

/**
 * AI 战斗单位实体（M1 实体化，docs/02 §3.12；M2 战斗逻辑）。
 * 原版 Steve 外观（PlayerModel + 阵营 PMC 皮肤，slim 细臂），主手装配 SBW 枪械，
 * 服务端目标驱动：发现敌方（真人玩家 + 敌方 AI）→ GunShootGoal 瞄准开火（SBW 抛射物）。
 * 每个 AI 携带独立战利品容器（武器/弹药），死亡后随尸体掉落供玩家搜刮。
 * 阵营以 SynchedEntityData 同步（渲染器按阵营切换皮肤）+ NBT 持久化（服务端跨存档）。
 */
public class AiCombatantEntity extends PathfinderMob implements Container {
    /** 阵营同步键：普通字段不跨客户端同步（渲染器按阵营切皮肤），用实体数据流 */
    private static final EntityDataAccessor<Integer> DATA_FACTION_ID = SynchedEntityData.defineId(AiCombatantEntity.class, EntityDataSerializers.INT);
    /** 战利品容器容量：0=副武器（带弹）、1=手枪弹、2=步枪弹、3+=杂项（死亡掉落供玩家搜刮） */
    private static final int LOOT_SLOTS = 9;
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

    private final SimpleContainer loot = new SimpleContainer(LOOT_SLOTS);
    /** 临时探针计数器（排障后删）：避开 gameTime 门控的相位偏移假阴性 */
    private int probeCounter;

    public AiCombatantEntity(EntityType<? extends AiCombatantEntity> type, Level level) {
        super(type, level);
        // 永久存在：不自然消失，生命周期由 AiManager 顶替/补位/击杀清理管理
        setPersistenceRequired();
        if (!level.isClientSide) {
            registerCombatGoals();
        }
    }

    /** 服务端战斗 goal：目标选择（敌方真人 + 敌方 AI）+ SBW 枪械射击（mob_guns 数据缺失时退化为只索敌不开火） */
    private void registerCombatGoals() {
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, this::isEnemy));
        MobGunData gunData = MobGunData.from(this);
        if (gunData != null) {
            this.goalSelector.addGoal(1, new GunShootGoal<>(this, gunData));
        }
    }

    /** 敌我识别（服务端权威，docs/02 §3.1）：仅敌方阵营真人玩家与敌方 AI 为目标；未分配（NONE）不算敌人 */
    private boolean isEnemy(LivingEntity target) {
        if (target == this || target.isDeadOrDying()) {
            return false;
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
     * 战利品容器放副武器（带弹）+ 弹药，死亡掉落供玩家搜刮（主武器只在主手、避免重复掉落）。
     */
    public void equipLoadout() {
        String gunId = switch (getFaction()) {
            case LONESTAR -> GUN_ID_LONESTAR;
            case MANTICORE -> GUN_ID_MANTICORE;
            case VALKYRA -> GUN_ID_VALKYRA;
            default -> GUN_ID_FALLBACK;
        };
        setItemInHand(InteractionHand.MAIN_HAND, createGun(gunId));
        loot.setItem(0, createGun(GUN_ID_SIDEARM));
        loot.setItem(1, createAmmo(AMMO_ID_HANDGUN, 32));
        loot.setItem(2, createAmmo(AMMO_ID_RIFLE, 64));
        // 临时探针：验证战斗链路（mob_guns 数据注册 + 主手枪弹药写入），排障后删除
        ItemStack main = getMainHandItem();
        if (main.getItem() instanceof GunItem) {
            try {
                GunData mainData = GunData.from(main);
                MercenarySandboxShooter.LOGGER.info(
                        "MSB AI equip: faction={} mobGunData={} main={} virtualAmmo={} ammo={}",
                        getFaction(), MobGunData.from(this) != null,
                        BuiltInRegistries.ITEM.getKey(main.getItem()),
                        mainData.virtualAmmo.get(), mainData.ammo.get());
            } catch (Exception e) {
                MercenarySandboxShooter.LOGGER.warn("MSB AI equip probe failed: {}", e.toString());
            }
        }
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
        // 临时探针（排障后删）：有目标时每 20 次 lazyTick（约 4s）打印交战链路关键值
        probeCounter++;
        LivingEntity probeTarget = getTarget();
        if (probeTarget != null && probeCounter % 20 == 0) {
            try {
                ItemStack mh = getMainHandItem();
                GunData md = mh.getItem() instanceof GunItem ? GunData.from(mh) : null;
                CustomData cdData = mh.get(DataComponents.CUSTOM_DATA);
                CompoundTag cd = cdData == null ? null : cdData.copyTag();
                int nbtVA = cd == null ? -1 : cd.getCompound("GunData").getInt("VirtualAmmo");
                int nbtAmmo = cd == null ? -1 : cd.getCompound("GunData").getInt("Ammo");
                MobGunData mgd = MobGunData.from(this);
                GunData jg = mgd == null ? null : mgd.getGunData();
                MercenarySandboxShooter.LOGGER.info(
                        "MSB AI probe: name={} target={} los={} dist={} jsonVA={} mdAmmo={} mdVA={} nbtVA={} nbtAmmo={} canShoot={}",
                        getCustomName() == null ? getStringUUID() : getCustomName().getString(),
                        probeTarget.getName().getString(),
                        getSensing().hasLineOfSight(probeTarget),
                        (int) Math.sqrt(distanceToSqr(probeTarget)),
                        jg == null ? -1 : jg.virtualAmmo.get(),
                        md == null ? -1 : md.ammo.get(),
                        md == null ? -1 : md.virtualAmmo.get(),
                        nbtVA, nbtAmmo,
                        md != null && md.canShoot(this));
            } catch (Exception e) {
                MercenarySandboxShooter.LOGGER.warn("MSB AI probe failed: {}", e.toString());
            }
        } else if (probeTarget == null && probeCounter % 200 == 0) {
            MercenarySandboxShooter.LOGGER.info("MSB AI probe: name={} target=none",
                    getCustomName() == null ? getStringUUID() : getCustomName().getString());
        }
        if (isRemoved() || isDeadOrDying() || getTarget() != null) {
            return;
        }
        double dx = zoneCenter.getX() + 0.5D - getX();
        double dz = zoneCenter.getZ() + 0.5D - getZ();
        boolean inZone = dx * dx + dz * dz <= (double) radius * radius;
        if (inZone) {
            getNavigation().stop();
        } else {
            getNavigation().moveTo(zoneCenter.getX() + 0.5D, zoneCenter.getY(), zoneCenter.getZ() + 0.5D, 0.8D);
        }
    }

    /** 死亡掉落：主手武器只掉一把 + 战利品容器内容（先摘主手再走原版流程，防原版装备掉落叠加） */
    @Override
    public void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // 摘除主手后 super 无从掉落它（原版也会掉主手装备，不摘会与下方掉落重复成两把主武器）
        ItemStack mainHand = getMainHandItem();
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        super.dropAllDeathLoot(level, source);
        // 战利品容器内容（副武器 + 弹药）掉落供玩家搜刮
        for (int i = 0; i < loot.getContainerSize(); i++) {
            ItemStack stack = loot.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        loot.clearContent();
        if (!mainHand.isEmpty()) {
            spawnAtLocation(mainHand);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Faction", getFaction().name());
        ContainerHelper.saveAllItems(tag, loot.getItems(), this.registryAccess());
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
        ContainerHelper.loadAllItems(tag, loot.getItems(), this.registryAccess());
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
