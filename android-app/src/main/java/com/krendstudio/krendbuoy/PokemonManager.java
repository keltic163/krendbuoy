package com.krendstudio.krendbuoy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.krendstudio.krendbuoy.PokemonConstants.*;

public class PokemonManager {
    private int moneyAddress = 0;
    private int lockedMoney = -1;
    private int securityKey = 0;
    private int saveBlock1Addr = 0;
    private int saveBlock2Addr = 0;
    private GameVersion manualVersion = GameVersion.UNKNOWN;

    private int offItems = 0, offKey = 0, offBalls = 0, offTM = 0, offBerries = 0;

    private final Map<Integer, String> itemNameCache = new HashMap<>();
    private boolean namesLoaded = false;

    public void setVersion(GameVersion version) {
        manualVersion = version == null ? GameVersion.UNKNOWN : version;
        autoLocateByPointers();
    }

    public void clearManualVersion() {
        manualVersion = GameVersion.UNKNOWN;
        autoLocateByPointers();
    }

    public boolean isManualVersionSelected() { return manualVersion != GameVersion.UNKNOWN; }
    public GameVersion getManualVersion() { return manualVersion; }
    public GameVersion getEffectiveVersion() {
        return manualVersion != GameVersion.UNKNOWN ? manualVersion : detectVersion();
    }

    private boolean usesRseLayout(GameVersion v) {
        return v == GameVersion.RUBY || v == GameVersion.SAPPHIRE || v == GameVersion.EMERALD;
    }

    private boolean usesSecurityKey(GameVersion v) {
        return v != GameVersion.RUBY && v != GameVersion.SAPPHIRE && v != GameVersion.UNKNOWN;
    }

    public boolean autoLocateByPointers() {
        GameVersion v = getEffectiveVersion();
        if (v == GameVersion.UNKNOWN) return false;

        boolean rse = usesRseLayout(v);
        int p1 = rse ? 0x03005D8C : 0x03005008;
        int p2 = rse ? 0x03005D90 : 0x0300500C;

        byte[] b1 = NativeBridge.readMemory(p1, 4);
        byte[] b2 = NativeBridge.readMemory(p2, 4);

        if (b1 != null && b1.length == 4) {
            saveBlock1Addr = ByteBuffer.wrap(b1).order(ByteOrder.LITTLE_ENDIAN).getInt();
            moneyAddress = (saveBlock1Addr >= 0x02000000) ? saveBlock1Addr + (rse ? 0x490 : 0x290) : 0;
        }
        if (b2 != null && b2.length == 4) {
            saveBlock2Addr = ByteBuffer.wrap(b2).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (saveBlock2Addr >= 0x02000000) {
                // 嘗試搜尋金鑰
                scanForKeyHeuristically();
            }
        }
        calibratePockets(v);
        return saveBlock1Addr >= 0x02000000;
    }

    private void scanForKeyHeuristically() {
        if (!usesSecurityKey(getEffectiveVersion())) {
            securityKey = 0;
            return;
        }
        if (moneyAddress == 0) return;
        byte[] mData = readRawMoney();
        if (mData == null) return;
        int rawM = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // 在 SaveBlock2 數據區搜尋能解密出合理金額的 32-bit 數值
        byte[] data = NativeBridge.readMemory(saveBlock2Addr, 4000);
        if (data == null) return;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < data.length - 4; i += 4) {
            int key = bb.getInt(i);
            if (key == 0) continue;
            int dec = rawM ^ key;
            if (dec >= 0 && dec <= 1000000 && key != rawM) {
                this.securityKey = key;
                return;
            }
        }
        // 保底
        byte[] kb = NativeBridge.readMemory(saveBlock2Addr + 0xAC4, 4);
        if (kb != null) securityKey = ByteBuffer.wrap(kb).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private void calibratePockets(GameVersion v) {
        if (saveBlock1Addr == 0) return;
        boolean encrypted = usesSecurityKey(v) && securityKey != 0;
        int xorPart = securityKey & 0xFFFF;
        byte[] data = NativeBridge.readMemory(saveBlock1Addr, 5000); // 釉色版範圍擴大
        if (data == null) return;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        offItems = 0; offKey = 0; offBalls = 0;

        for (int i = 0x200; i < data.length - 8; i += 2) {
            int id = bb.getShort(i) & 0xFFFF;
            int count = bb.getShort(i + 2) & 0xFFFF;
            int dec = encrypted ? (count ^ xorPart) : count;

            if (id >= 0x01 && id <= 0x0C && dec >= 1 && dec <= 99 && offBalls == 0) offBalls = i;
            if (id >= 0x0D && id <= 0x60 && dec >= 1 && dec <= 999 && offItems == 0) offItems = i;
        }

        boolean rse = usesRseLayout(v);
        if (offItems == 0) offItems = rse ? 0x498 : 0x298;
        if (offBalls == 0) offBalls = rse ? 0x5D8 : 0x360;
        offKey = offBalls - 120; // 相對定位法
        offTM = offBalls + (rse ? 0x40 : 0x34);
        offBerries = offTM + (rse ? 0x100 : 0xC8);
    }

    public int scanForTrainerID(int tid) {
        autoLocateByPointers();
        if (saveBlock2Addr >= 0x02000000) {
            byte[] check = NativeBridge.readMemory(saveBlock2Addr + 0x0A, 2);
            if (check != null && (ByteBuffer.wrap(check).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF) == tid) {
                return saveBlock2Addr;
            }
        }

        byte[] ram = NativeBridge.readMemory(0x02000000, 0x00040000);
        if (ram == null) return 0;
        ByteBuffer bb = ByteBuffer.wrap(ram).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0x4000; i < ram.length - 12; i += 2) {
            if ((bb.getShort(i) & 0xFFFF) == tid) {
                saveBlock2Addr = 0x02000000 + i - 0x0A;
                scanForKeyHeuristically();
                findSB1NearSB2(saveBlock2Addr);
                calibratePockets(getEffectiveVersion());
                return saveBlock2Addr;
            }
        }
        return 0;
    }

    private void findSB1NearSB2(int sb2) {
        int start = Math.max(0x02000000, sb2 - 0x8000);
        byte[] data = NativeBridge.readMemory(start, 0x10000);
        if (data == null) return;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        boolean encrypted = usesSecurityKey(getEffectiveVersion()) && securityKey != 0;
        for (int i = 0; i < data.length - 4; i += 4) {
            int val = bb.getInt(i);
            int dec = encrypted ? (val ^ securityKey) : val;
            if (val != 0 && dec >= 0 && dec <= 999999) {
                this.moneyAddress = start + i;
                saveBlock1Addr = this.moneyAddress - (usesRseLayout(getEffectiveVersion()) ? 0x490 : 0x290);
                return;
            }
        }
    }

    public int getMoney() {
        byte[] data = readRawMoney();
        if (data == null || data.length < 4) return -1;
        int raw = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (usesSecurityKey(getEffectiveVersion()) && securityKey != 0) {
            int dec = raw ^ securityKey;
            if (dec >= 0 && dec <= 2000000) return dec;
        }
        return (raw >= 0 && raw <= 1000000) ? raw : -1;
    }

    public boolean setMoney(int value) {
        if (moneyAddress == 0) return false;
        int toWrite = (usesSecurityKey(getEffectiveVersion()) && securityKey != 0) ? value ^ securityKey : value;
        byte[] data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(toWrite).array();
        return NativeBridge.writeMemory(moneyAddress, data);
    }

    public void lockMoney(int val) { this.lockedMoney = val; }
    public void unlockMoney() { this.lockedMoney = -1; }
    public boolean isMoneyLocked() { return lockedMoney != -1; }
    public void applyLocks() { if (lockedMoney != -1) setMoney(lockedMoney); }

    public int scanForTrainerName(String name) {
        byte[] target; try { target = name.getBytes("GBK"); } catch (Exception e) { return 0; }
        byte[] ram = NativeBridge.readMemory(0x02020000, 0x00018000);
        if (ram == null) return 0;
        for (int i = 0; i < ram.length - 100; i++) {
            boolean m = true; for (int j = 0; j < target.length; j++) if (ram[i+j] != target[j]) { m = false; break; }
            if (m) { saveBlock2Addr = 0x02020000 + i; autoLocateByPointers(); return saveBlock2Addr; }
        }
        return 0;
    }

    public void scanByExactMoney(int amount) {
        byte[] mData = readRawMoney();
        if (mData != null && usesSecurityKey(getEffectiveVersion())) {
            // 核心：利用真實金額與 Raw 數據直接算出 Security Key
            this.securityKey = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN).getInt() ^ amount;
            // 算出 Key 後，全口袋立即同步重校準
            calibratePockets(getEffectiveVersion());
        }
    }

    private int getPocketOffset(Pocket targetPocket) {
        if (targetPocket == Pocket.ITEMS) return offItems;
        if (targetPocket == Pocket.KEY_ITEMS) return offKey;
        if (targetPocket == Pocket.BALLS) return offBalls;
        if (targetPocket == Pocket.TM_HM) return offTM;
        if (targetPocket == Pocket.BERRIES) return offBerries;
        return 0;
    }

    private int getPocketCapacity(GameVersion version, Pocket targetPocket) {
        if (targetPocket == null || version == GameVersion.UNKNOWN) return 0;
        boolean rse = usesRseLayout(version);
        if (targetPocket == Pocket.BALLS) return rse ? 20 : 15;
        return rse ? 50 : 30;
    }

    private int findFirstEmptyPocketSlot(Pocket targetPocket) {
        GameVersion v = getEffectiveVersion();
        if (saveBlock1Addr == 0 || (usesSecurityKey(v) && securityKey == 0)) return -1;

        int off = getPocketOffset(targetPocket);
        int max = getPocketCapacity(v, targetPocket);
        if (off == 0 || max <= 0) return -1;

        byte[] data = NativeBridge.readMemory(saveBlock1Addr + off, max * 4);
        if (data == null) return -1;

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < max; i++) {
            int pos = i * 4;
            if (pos + 2 > data.length) break;

            int id = bb.getShort(pos) & 0xFFFF;
            if (id == 0 || id == 0x116 || id == 0x169) {
                return i;
            }
        }

        return -1;
    }

    public List<ItemSlot> getBagItems(Pocket targetPocket) {
        List<ItemSlot> items = new ArrayList<>();
        GameVersion v = getEffectiveVersion();
        if (saveBlock1Addr == 0 || (usesSecurityKey(v) && securityKey == 0)) return items;
        int xorPart = securityKey & 0xFFFF;
        int off = getPocketOffset(targetPocket);

        if (off == 0) return items;
        int max = getPocketCapacity(v, targetPocket);
        if (max <= 0) return items;

        byte[] data = NativeBridge.readMemory(saveBlock1Addr + off, max * 4);
        if (data == null) return items;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < max; i++) {
            if (bb.remaining() < 4) break;
            int id = bb.getShort() & 0xFFFF;
            int rawC = bb.getShort() & 0xFFFF;
            if (id == 0 || id > 1000 || id == 0x116 || id == 0x169) continue;

            int realC = (usesSecurityKey(v) && targetPocket != Pocket.KEY_ITEMS && id <= 0x100) ? (rawC ^ xorPart) : rawC;
            if (realC > 20000) realC = rawC;
            items.add(new ItemSlot(id, realC, i, targetPocket));
        }
        return items;
    }

    public boolean setBagItem(int slotIndex, int itemId, int count, Pocket p) {
        GameVersion v = getEffectiveVersion();
        if (saveBlock1Addr == 0 || (usesSecurityKey(v) && securityKey == 0)) return false;

        int off = getPocketOffset(p);
        if (off == 0) return false;

        int max = getPocketCapacity(v, p);
        if (slotIndex < 0 || slotIndex >= max) return false;

        int addr = saveBlock1Addr + off + (slotIndex * 4);
        int xorPart = securityKey & 0xFFFF;
        int toWrite = (usesSecurityKey(v) && p != Pocket.KEY_ITEMS && itemId <= 0x100) ? ((count & 0xFFFF) ^ xorPart) : count;
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short)itemId); bb.putShort((short)toWrite);
        return NativeBridge.writeMemory(addr, bb.array());
    }

    public boolean addBagItem(int itemId, int count, Pocket p) {
        int slot = findFirstEmptyPocketSlot(p);
        if (slot < 0) return false;
        return setBagItem(slot, itemId, count, p);
    }

    public List<ItemInfo> getCommonItems(Pocket p) { return PokemonConstants.getCommonItems(p); }
    public String getItemName(int id) { return PokemonConstants.getItemName(id); }
    public int findSecurityKey() { return securityKey; }
    public int getMoneyAddress() { return moneyAddress; }
    public byte[] readRawMoney() { return moneyAddress == 0 ? null : NativeBridge.readMemory(moneyAddress, 4); }
    public GameVersion detectVersion() {
        byte[] data = NativeBridge.readMemory(0x080000A0, 12);
        if (data == null) return GameVersion.UNKNOWN;
        String name = new String(data).toUpperCase();
        if (name.contains("GLAZED") || name.contains("POKEMON EMER")) return GameVersion.EMERALD;
        if (name.contains("POKEMON FIRE")) return GameVersion.FIRE_RED;
        if (name.contains("POKEMON LEAF")) return GameVersion.LEAF_GREEN;
        if (name.contains("POKEMON RUBY")) return GameVersion.RUBY;
        if (name.contains("POKEMON SAPP")) return GameVersion.SAPPHIRE;
        return GameVersion.UNKNOWN;
    }
}
