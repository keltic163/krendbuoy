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
    private final Map<Integer, String> speciesNameCache = new HashMap<>();
    private boolean namesLoaded = false;
    private boolean speciesLoaded = false;
    private String gameCode = "";

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
            if (saveBlock1Addr >= 0x02000000 && saveBlock1Addr <= 0x02048000) {
                moneyAddress = saveBlock1Addr + (rse ? 0x490 : 0x290);
            }
        }
        if (b2 != null && b2.length == 4) {
            saveBlock2Addr = ByteBuffer.wrap(b2).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (saveBlock2Addr >= 0x02000000) {
                scanForKeyHeuristically();
            }
        }
        calibratePockets(v);
        return saveBlock1Addr >= 0x02000000;
    }

    private void scanForKeyHeuristically() {
        GameVersion v = getEffectiveVersion();
        if (!usesSecurityKey(v)) {
            securityKey = 0;
            return;
        }
        
        // Priority 1: Standard offset in SaveBlock2 (most stable)
        int stdKeyOff = usesRseLayout(v) ? 0x0AF8 : 0x0AC4;
        byte[] stdKeyData = NativeBridge.readMemory(saveBlock2Addr + stdKeyOff, 4);
        if (stdKeyData != null) {
            int key = ByteBuffer.wrap(stdKeyData).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (key != 0) {
                this.securityKey = key;
                return;
            }
        }

        // Priority 2: Scan for key based on money
        if (moneyAddress != 0) {
            byte[] mData = readRawMoney();
            if (mData != null) {
                int rawM = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN).getInt();
                byte[] data = NativeBridge.readMemory(saveBlock2Addr, 4000);
                if (data != null) {
                    ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < data.length - 4; i += 4) {
                        int key = bb.getInt(i);
                        int dec = rawM ^ key;
                        if (dec >= 0 && dec <= 999999 && key != 0) {
                            this.securityKey = key;
                            return;
                        }
                    }
                }
            }
        }
    }

    private void calibratePockets(GameVersion v) {
        if (saveBlock1Addr == 0) return;
        boolean encrypted = usesSecurityKey(v) && securityKey != 0;
        int xorPart = securityKey & 0xFFFF;
        
        // Dynamic scan with STRICT validation (Check sequence of 3 items to avoid money false positive)
        byte[] data = NativeBridge.readMemory(saveBlock1Addr, 5000);
        if (data == null) return;
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        offItems = 0; offKey = 0; offBalls = 0;

        for (int i = 0x100; i < data.length - 12; i += 2) {
            int id = bb.getShort(i) & 0xFFFF;
            if (id >= 1 && id <= 0x176) {
                int id2 = bb.getShort(i + 4) & 0xFFFF;
                int id3 = bb.getShort(i + 8) & 0xFFFF;
                if (id2 > 0 && id2 <= 0x200 && id3 > 0 && id3 <= 0x200) {
                    int rawCount = bb.getShort(i + 2) & 0xFFFF;
                    int dec = encrypted ? (rawCount ^ xorPart) : rawCount;

                    if (id >= 0x01 && id <= 0x0C && dec >= 1 && dec <= 999 && offBalls == 0) offBalls = i;
                    if (id >= 0x0D && id <= 0x60 && dec >= 1 && dec <= 999 && offItems == 0) offItems = i;
                }
            }
        }

        boolean rse = usesRseLayout(v);
        if (offItems == 0) offItems = rse ? 0x498 : 0x298;
        if (offBalls == 0) offBalls = rse ? 0x5D8 : 0x3B8;
        offKey = offBalls - (rse ? 80 : 120);
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
        
        // Priority: Use derived security key
        if (securityKey != 0) {
            return raw ^ securityKey;
        }
        
        // Check if raw value itself is reasonable (unencrypted)
        if (raw >= 0 && raw <= 1000000) return raw;
        
        return -1;
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
            this.securityKey = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN).getInt() ^ amount;
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
            if (id == 0 || id == 0x116 || id == 0x169) return i;
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
            
            if (id == 0 || id == 0x116 || id == 0x169) continue;
            if (targetPocket == Pocket.BALLS && (id < 1 || id > 0x010)) continue;
            if (id > 0x176) continue;

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

    public List<PokemonEntry> getParty() {
        List<PokemonEntry> team = new ArrayList<>();
        if (saveBlock2Addr == 0) return team;
        GameVersion v = getEffectiveVersion();
        boolean rse = usesRseLayout(v);
        
        // 1. Try standard offsets first (with wider range)
        int[] offsets = rse ? new int[]{0x0234, 0x0238, 0x0240} : new int[]{0x0034, 0x0038, 0x0040};
        for (int off : offsets) {
            if (scanPartyAtOffset(team, saveBlock2Addr + off)) return team;
        }
        
        // 2. Perform a thorough scan near SB2 (compensates for shifted hack structures)
        // Search 2KB around SB2
        byte[] nearData = NativeBridge.readMemory(saveBlock2Addr, 2048);
        if (nearData != null) {
            for (int i = 0x20; i < nearData.length - 100; i += 4) {
                if (scanPartyAtOffset(team, saveBlock2Addr + i)) return team;
            }
        }
        
        // 3. Last Resort: Brute-force RAM for Party Signature (PV, OTID, Nickname pattern)
        // This is slow, so we only do it if the Trainer button was explicitly clicked
        return team;
    }

    private boolean scanPartyAtOffset(List<PokemonEntry> team, int startAddr) {
        byte[] data = NativeBridge.readMemory(startAddr, 100); // Check only first slot for speed
        if (data == null) return false;
        
        PokemonEntry first = new PokemonEntry(data, startAddr);
        if (first.species > 0 && first.species <= 1000) {
            // Found a valid first slot, read the full party (6 slots)
            byte[] fullData = NativeBridge.readMemory(startAddr, 600);
            if (fullData == null) return false;
            
            team.clear();
            for (int i = 0; i < 6; i++) {
                byte[] pkmnRaw = new byte[100];
                System.arraycopy(fullData, i * 100, pkmnRaw, 0, 100);
                PokemonEntry entry = new PokemonEntry(pkmnRaw, startAddr + (i * 100));
                if (entry.species > 0 && entry.species <= 1000) team.add(entry);
            }
            return !team.isEmpty();
        }
        return false;
    }
    
    public int getSaveBlock2Addr() { return saveBlock2Addr; }
    
    public int findSecurityKey() { return securityKey; }
    public int getMoneyAddress() { return moneyAddress; }
    public byte[] readRawMoney() { return moneyAddress == 0 ? null : NativeBridge.readMemory(moneyAddress, 4); }

    public GameVersion detectVersion() {
        byte[] data = NativeBridge.readMemory(0x080000AC, 4); 
        if (data == null) return GameVersion.UNKNOWN;
        gameCode = new String(data).toUpperCase();
        if (gameCode.startsWith("BPR")) return GameVersion.FIRE_RED;
        if (gameCode.startsWith("BPG")) return GameVersion.LEAF_GREEN;
        if (gameCode.startsWith("BPE")) return GameVersion.EMERALD;
        if (gameCode.startsWith("AXV")) return GameVersion.RUBY;
        if (gameCode.startsWith("AXP")) return GameVersion.SAPPHIRE;
        byte[] nameData = NativeBridge.readMemory(0x080000A0, 16);
        if (nameData != null) {
            String name = new String(nameData).toUpperCase();
            if (name.contains("GLAZED")) return GameVersion.EMERALD;
        }
        return GameVersion.UNKNOWN;
    }

    public String getItemName(int id) {
        if (id == 0) return "---";
        
        // Strategy: Use built-in official high-quality database (highest stability)
        String dbName = PokemonConstants.getItemName(id);
        if (!dbName.equals("未知道具")) {
            return dbName;
        }
        
        // Fallback to ROM text only for IDs outside the official range (Custom Hack items)
        if (!namesLoaded) loadItemNamesFromRom();
        String cached = itemNameCache.get(id);
        return (cached != null) ? cached : "未知道具";
    }

    public String getSpeciesName(int id) {
        if (id == 0) return "---";
        
        // 1. Priority: Use built-in high-quality Traditional Chinese database
        String dbName = PokemonConstants.getSpeciesName(id);
        if (!dbName.startsWith("未知精靈")) return dbName;
        
        // 2. Fallback to ROM text
        if (!speciesLoaded) loadSpeciesNamesFromRom();
        String cached = speciesNameCache.get(id);
        return cached != null ? cached : dbName;
    }

    public String getPokemonNickname(PokemonEntry p) {
        if (p == null) return "";
        byte[] nickBytes = new byte[10];
        System.arraycopy(p.getRawData(), 8, nickBytes, 0, 10);
        String nick = decodeGbaText(nickBytes);
        return (nick == null || nick.isEmpty()) ? getSpeciesName(p.species) : nick;
    }

    private void loadItemNamesFromRom() {
        if (namesLoaded) return;
        detectVersion(); // Ensure gameCode is current
        int tableAddr = findTableBySignature(44, 1, 2, 3); // Signature: IDs 1, 2, 3 at offset 14
        if (tableAddr == 0) return;
        for (int i = 0; i < 512; i++) {
            byte[] nameBytes = NativeBridge.readMemory(tableAddr + (i * 44), 14);
            if (nameBytes == null) break;
            if (i > 0 && nameBytes[0] == 0x00 && nameBytes[1] == 0x00) break;
            String name = decodeGbaText(nameBytes);
            if (name.length() >= 2) itemNameCache.put(i, name);
        }
        namesLoaded = true;
    }

    private void loadSpeciesNamesFromRom() {
        if (speciesLoaded) return;
        detectVersion();
        // Species table: ID is NOT in the struct, it's just a sequence of 11-byte names
        // Search by known base offsets based on gameCode
        int tableAddr = 0;
        if (gameCode.startsWith("BPR")) tableAddr = 0x08245EE0;
        else if (gameCode.startsWith("BPG")) tableAddr = 0x08245EE0;
        else if (gameCode.startsWith("BPE")) tableAddr = 0x083185C8;
        else if (gameCode.startsWith("AXV")) tableAddr = 0x081F716C;
        else if (gameCode.startsWith("AXP")) tableAddr = 0x081F716C;
        
        if (tableAddr == 0) return;
        for (int i = 0; i < 412; i++) {
            byte[] nameBytes = NativeBridge.readMemory(tableAddr + (i * 11), 11);
            if (nameBytes == null) break;
            if (i > 0 && nameBytes[0] == 0x00) break;
            String name = decodeGbaText(nameBytes);
            if (name.length() >= 2) speciesNameCache.put(i, name);
        }
        speciesLoaded = true;
    }

    private int findTableBySignature(int entrySize, int id1, int id2, int id3) {
        int start = 0x08100000; // ROM space
        int range = 0x00800000; // Search 8MB
        byte[] data = NativeBridge.readMemory(start, range);
        if (data == null) return 0;
        for (int i = 0; i < data.length - (entrySize * 3); i += 4) {
            int check1 = (data[i + 14] & 0xFF) | ((data[i + 15] & 0xFF) << 8);
            if (check1 == id1) {
                int check2 = (data[i + entrySize + 14] & 0xFF) | ((data[i + entrySize + 15] & 0xFF) << 8);
                if (check2 == id2) {
                    int check3 = (data[i + entrySize * 2 + 14] & 0xFF) | ((data[i + entrySize * 2 + 15] & 0xFF) << 8);
                    if (check3 == id3) return start + i;
                }
            }
        }
        return 0;
    }

    private String decodeGbaText(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            int u = data[i] & 0xFF;
            if (u == 0xFF) break;
            
            // Check for 2-byte Chinese lead byte (A1-FE)
            if (u >= 0xA1 && u <= 0xFE && i + 1 < data.length) {
                int u2 = data[i+1] & 0xFF;
                if (u2 >= 0x40 && u2 <= 0xFE) {
                    try {
                        byte[] gbk = {(byte)u, (byte)u2};
                        String s = new String(gbk, "GBK");
                        sb.append(s);
                        i++; continue;
                    } catch (Exception ignored) {}
                }
            }
            
            // Standard Pokemon Latin encoding mapping
            if (u == 0x00) { sb.append(" "); continue; }
            if (u >= 0xBB && u <= 0xD4) { sb.append((char) ('A' + (u - 0xBB))); continue; }
            if (u >= 0xD5 && u <= 0xEE) { sb.append((char) ('a' + (u - 0xD5))); continue; }
            if (u >= 0xA1 && u <= 0xAA) { sb.append((char) ('0' + (u - 0xA1))); continue; }
            if (u == 0xAB) sb.append("!");
            if (u == 0xAC) sb.append("?");
            if (u == 0xAD) sb.append(".");
            if (u == 0xAE) sb.append("-");
            if (u == 0x2D) sb.append("&");
        }
        return sb.toString().trim().replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "");
    }
}
