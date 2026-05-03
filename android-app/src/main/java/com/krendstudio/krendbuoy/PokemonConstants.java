package com.krendstudio.krendbuoy;

import java.util.ArrayList;
import java.util.List;

public final class PokemonConstants {
    public enum GameVersion { UNKNOWN, FIRE_RED, LEAF_GREEN, EMERALD }
    public enum Pocket { ITEMS, BALLS, KEY_ITEMS, BERRIES, TM_HM }

    public static class ItemSlot {
        public int id, count, index;
        public Pocket pocket;
        public ItemSlot(int id, int count, int index, Pocket pocket) { 
            this.id = id; this.count = count; this.index = index; this.pocket = pocket;
        }
    }

    public static class ItemInfo {
        public final int id; public final String name; public final Pocket pocket;
        public ItemInfo(int id, String name, Pocket pocket) { 
            this.id = id; this.name = name; this.pocket = pocket;
        }
    }

    public static String getItemName(int id) {
        if (id == 0x116 || id == 0x169) return "CANCEL";
        if (id >= 0x01 && id <= 0x0C) {
            if (id == 0x01) return "Master Ball";
            if (id == 0x02) return "Ultra Ball";
            if (id == 0x03) return "Great Ball";
            if (id == 0x04) return "Poke Ball";
            return "Ball #" + id;
        }
        switch(id) {
            case 0x0D: return "Potion";
            case 0x13: return "Full Restore";
            case 0x19: return "Revive";
            case 0x1A: return "Max Revive";
            case 0x21: return "Full Heal";
            case 0x44: return "Rare Candy";
            case 0x4B: return "PP Up";
            case 0x4C: return "PP Max";
            case 0x53: return "Super Repel";
            case 0xFE: return "Exp. Share";
            case 0x103: return "Town Map";
            case 0x10F: return "Old Rod";
            case 0x110: return "Good Rod";
            case 0x111: return "Super Rod";
            default: return "Item #" + id;
        }
    }

    public static List<ItemInfo> getCommonItems(Pocket p) {
        List<ItemInfo> list = new ArrayList<>();
        if (p == Pocket.BALLS) {
            list.add(new ItemInfo(0x01, "Master Ball", Pocket.BALLS));
            list.add(new ItemInfo(0x02, "Ultra Ball", Pocket.BALLS));
            list.add(new ItemInfo(0x04, "Poke Ball", Pocket.BALLS));
        } else if (p == Pocket.ITEMS) {
            list.add(new ItemInfo(0x44, "Rare Candy", Pocket.ITEMS));
            list.add(new ItemInfo(0x13, "Full Restore", Pocket.ITEMS));
            list.add(new ItemInfo(0xFE, "Exp. Share", Pocket.ITEMS));
        } else if (p == Pocket.KEY_ITEMS) {
            list.add(new ItemInfo(0x103, "Town Map", Pocket.KEY_ITEMS));
            list.add(new ItemInfo(0x111, "Super Rod", Pocket.KEY_ITEMS));
        }
        return list;
    }
}
