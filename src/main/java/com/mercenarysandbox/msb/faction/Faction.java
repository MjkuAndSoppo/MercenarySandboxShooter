package com.mercenarysandbox.msb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.ChatFormatting;

/**
 * 三大阵营（WARDOGS 玩法层）。
 * M1 起用于：计分板队伍映射、玩家阵营附体、HUD 计分与 Tab/名字染色。
 */
public enum Faction {
    /** 红 · SANGVIS FERRI（S.F · 铁血工业制造）：人力5 火力5 补给3 上限2 */
    LONESTAR(0, ChatFormatting.RED, "msb_lonestar", "team.msb.lonestar", "S.F", 5, 5, 3, 2),
    /** 蓝 · Special Operations Forces Command（КССО/KCCO · 新苏联特战部）：人力2 火力4 补给5 上限4 */
    VALKYRA(1, ChatFormatting.BLUE, "msb_valkyra", "team.msb.valkyra", "KCCO", 2, 4, 5, 4),
    /** 绿 · Important Operation Prototype（I.O.P · 重要原型制造）：人力4 火力3 补给3 上限5 */
    MANTICORE(2, ChatFormatting.GREEN, "msb_manticore", "team.msb.manticore", "I.O.P", 4, 3, 3, 5),
    /** 未分配哨兵（附体默认值，加入时会被替换） */
    NONE(3, ChatFormatting.GRAY, "msb_none", "team.msb.none", "", 0, 0, 0, 0);

    /** 网络传输用整数 id（独立于枚举序，避免重排破坏存档兼容） */
    private final int id;
    private final ChatFormatting chatColor;
    /** 计分板队伍名（Scoreboard 队伍名 ≤16 字符） */
    private final String teamName;
    /** 队伍显示名语言键 */
    private final String displayKey;
    /** 阵营缩写（AI 命名用，如 S.Fpmc.1） */
    private final String abbr;
    /** 阵营增益（仅展示与数据结构，暂不接实际效果；取值 0~5） */
    private final int manpower;
    private final int firepower;
    private final int supply;
    private final int cap;

    Faction(int id, ChatFormatting chatColor, String teamName, String displayKey, String abbr,
            int manpower, int firepower, int supply, int cap) {
        this.id = id;
        this.chatColor = chatColor;
        this.teamName = teamName;
        this.displayKey = displayKey;
        this.abbr = abbr;
        this.manpower = manpower;
        this.firepower = firepower;
        this.supply = supply;
        this.cap = cap;
    }

    /** 人力增益（0~5） */
    public int getManpower() {
        return manpower;
    }

    /** 火力增益（0~5） */
    public int getFirepower() {
        return firepower;
    }

    /** 补给增益（0~5） */
    public int getSupply() {
        return supply;
    }

    /** 上限增益（0~5） */
    public int getCap() {
        return cap;
    }

    public int getId() {
        return id;
    }

    public ChatFormatting getChatColor() {
        return chatColor;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public String getAbbr() {
        return abbr;
    }

    /** 按网络传输 id 反查阵营（SynchedEntityData 同步用） */
    public static Faction byId(int id) {
        for (Faction f : values()) {
            if (f.id == id) {
                return f;
            }
        }
        return NONE;
    }

    /** 附体序列化用 Codec（字符串名；未知值报错，由调用方兜底 NONE） */
    public static final Codec<Faction> CODEC = Codec.STRING.flatXmap(
            name -> {
                for (Faction f : values()) {
                    if (f.name().equalsIgnoreCase(name)) {
                        return DataResult.success(f);
                    }
                }
                return DataResult.error(() -> "未知阵营: " + name);
            },
            f -> DataResult.success(f.name()));

    /** 按网络 id 取阵营；未知 id 返回 NONE */
    public static Faction fromId(int id) {
        for (Faction f : values()) {
            if (f.id == id) {
                return f;
            }
        }
        return NONE;
    }
}
