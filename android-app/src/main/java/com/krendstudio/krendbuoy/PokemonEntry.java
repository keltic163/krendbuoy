package com.krendstudio.krendbuoy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a single Pokemon in a Gen 3 (GBA) party or PC box.
 * Handles the 100-byte structure, decryption, and block shuffling.
 */
public class PokemonEntry {
    public long pv;         // Personality Value (32-bit)
    public long otId;       // Original Trainer ID (32-bit)
    public String nickname;
    public int species;
    public int heldItem;
    public long exp;
    public int level;
    public int currentHp;
    public int maxHp;
    
    // Detailed stats
    public int hp, atk, def, spAtk, spDef, speed;
    public int hpEv, atkEv, defEv, spAtkEv, spDefEv, speedEv;
    public int hpIv, atkIv, defIv, spAtkIv, spDefIv, speedIv;
    public int isEgg, abilitySlot;
    private long originalIvDword;
    
    // Moves
    public int[] moves = new int[4];
    public int[] pp = new int[4];

    public int address;     // Memory address in RAM
    private byte[] rawData; // The 100-byte raw memory

    public byte[] getRawData() { return rawData; }

    public PokemonEntry(byte[] data, int address) {
        if (data == null || data.length < 100) return;
        this.rawData = data;
        this.address = address;
        parse();
    }

    private void parse() {
        ByteBuffer bb = ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN);
        pv = bb.getInt(0) & 0xFFFFFFFFL;
        otId = bb.getInt(4) & 0xFFFFFFFFL;
        
        byte[] nickBytes = new byte[10];
        System.arraycopy(rawData, 8, nickBytes, 0, 10);
        // Note: decodeGbaText is in PokemonManager, we'll let the UI or Manager handle it
        // but for internal storage we keep the bytes or raw string if we can.
        
        // Basic info from non-encrypted footer (starts at 80)
        level = bb.get(84) & 0xFF;
        currentHp = bb.getShort(86) & 0xFFFF;
        maxHp = bb.getShort(88) & 0xFFFF;
        atk = bb.getShort(90) & 0xFFFF;
        def = bb.getShort(92) & 0xFFFF;
        speed = bb.getShort(94) & 0xFFFF;
        spAtk = bb.getShort(96) & 0xFFFF;
        spDef = bb.getShort(98) & 0xFFFF;

        // Decrypt Substructures (GAFE)
        byte[] encrypted = new byte[48];
        System.arraycopy(rawData, 32, encrypted, 0, 48);
        byte[] decrypted = decrypt(encrypted, pv, otId);
        
        // Unshuffle Blocks
        int order = (int)(pv % 24);
        byte[][] blocks = new byte[4][12];
        for (int i = 0; i < 4; i++) {
            int blockIndex = getBlockIndex(order, i);
            System.arraycopy(decrypted, i * 12, blocks[blockIndex], 0, 12);
        }

        // Block 0: Growth
        ByteBuffer bG = ByteBuffer.wrap(blocks[0]).order(ByteOrder.LITTLE_ENDIAN);
        species = bG.getShort(0) & 0xFFFF;
        heldItem = bG.getShort(2) & 0xFFFF;
        exp = bG.getInt(4) & 0xFFFFFFFFL;

        // Block 1: Attacks
        ByteBuffer bA = ByteBuffer.wrap(blocks[1]).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 4; i++) {
            moves[i] = bA.getShort(i * 2) & 0xFFFF;
            pp[i] = bA.get(8 + i) & 0xFF;
        }

        // Block 2: Effort / Condition
        ByteBuffer bF = ByteBuffer.wrap(blocks[2]).order(ByteOrder.LITTLE_ENDIAN);
        hpEv = bF.get(0) & 0xFF;
        atkEv = bF.get(1) & 0xFF;
        defEv = bF.get(2) & 0xFF;
        speedEv = bF.get(3) & 0xFF;
        spAtkEv = bF.get(4) & 0xFF;
        spDefEv = bF.get(5) & 0xFF;

        // Block 3: Misc / IVs
        ByteBuffer bE = ByteBuffer.wrap(blocks[3]).order(ByteOrder.LITTLE_ENDIAN);
        originalIvDword = bE.getInt(4) & 0xFFFFFFFFL;
        hpIv = (int)(originalIvDword & 0x1F);
        atkIv = (int)((originalIvDword >> 5) & 0x1F);
        defIv = (int)((originalIvDword >> 10) & 0x1F);
        speedIv = (int)((originalIvDword >> 15) & 0x1F);
        spAtkIv = (int)((originalIvDword >> 20) & 0x1F);
        spDefIv = (int)((originalIvDword >> 25) & 0x1F);
        isEgg = (int)((originalIvDword >> 30) & 0x01);
        abilitySlot = (int)((originalIvDword >> 31) & 0x01);
    }

    private byte[] decrypt(byte[] data, long pv, long otId) {
        byte[] result = new byte[data.length];
        long key = pv; // Encryption key is the PV
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer res = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < data.length / 4; i++) {
            res.putInt(bb.getInt() ^ (int)key);
        }
        return result;
    }

    private int getBlockIndex(int order, int position) {
        // G=0, A=1, F=2, E=3
        String[] table = {
            "GAEF", "GAFE", "GEAF", "GEFA", "GFAE", "GFEA",
            "AGEF", "AGFE", "AEGF", "AEFG", "AFGE", "AFEG",
            "EGAF", "EGFA", "EAGF", "EAFG", "EFGA", "EFAG",
            "FGAE", "FGEA", "FAGE", "FAEG", "FEGA", "FEAG"
        };
        char c = table[order].charAt(position);
        if (c == 'G') return 0;
        if (c == 'A') return 1;
        if (c == 'F') return 2;
        return 3; // E
    }

    public int getNature() { return (int)(pv % 25); }
    
    public void setNature(int natureId) {
        setNatureAndShiny(natureId, isShiny());
    }

    public void setShiny(boolean shiny) {
        setNatureAndShiny(getNature(), shiny);
    }

    private void setNatureAndShiny(int natureId, boolean shiny) {
        if (natureId < 0 || natureId >= 25) return;
        int tid = (int)(otId & 0xFFFF);
        int sid = (int)((otId >> 16) & 0xFFFF);
        int targetXor = tid ^ sid;

        // Brute force 32-bit PV
        // We start searching from a random-ish point or the current PV to keep it consistent
        long startPv = pv & 0xFFFFFFFFL;
        for (long i = 0; i < 0xFFFFFF; i++) {
            long testPv = (startPv + i) & 0xFFFFFFFFL;
            if ((testPv % 25) == natureId) {
                int pvL = (int)(testPv & 0xFFFF);
                int pvH = (int)((testPv >> 16) & 0xFFFF);
                boolean currentIsShiny = (targetXor ^ pvL ^ pvH) < 8;
                if (currentIsShiny == shiny) {
                    pv = testPv;
                    return;
                }
            }
        }
    }

    public boolean isShiny() {
        int tid = (int)(otId & 0xFFFF);
        int sid = (int)((otId >> 16) & 0xFFFF);
        int pvL = (int)(pv & 0xFFFF);
        int pvH = (int)((pv >> 16) & 0xFFFF);
        return (tid ^ sid ^ pvL ^ pvH) < 8;
    }

    public byte[] toRaw() {
        byte[] output = new byte[100];
        System.arraycopy(rawData, 0, output, 0, 100);
        ByteBuffer bb = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        
        // Update Personality and OTID (in case they were changed)
        bb.putInt(0, (int)pv);
        bb.putInt(4, (int)otId);

        // Prepare Substructures
        byte[][] blocks = new byte[4][12];
        
        // Block 0: Growth
        ByteBuffer bG = ByteBuffer.wrap(blocks[0]).order(ByteOrder.LITTLE_ENDIAN);
        bG.putShort(0, (short)species);
        bG.putShort(2, (short)heldItem);
        bG.putInt(4, (int)exp);

        // Block 1: Attacks
        ByteBuffer bA = ByteBuffer.wrap(blocks[1]).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 4; i++) {
            bA.putShort(i * 2, (short)moves[i]);
            bA.put(8 + i, (byte)pp[i]);
        }

        // Block 2: Effort
        ByteBuffer bF = ByteBuffer.wrap(blocks[2]).order(ByteOrder.LITTLE_ENDIAN);
        bF.put(0, (byte)hpEv); bF.put(1, (byte)atkEv); bF.put(2, (byte)defEv);
        bF.put(3, (byte)speedEv); bF.put(4, (byte)spAtkEv); bF.put(5, (byte)spDefEv);

        // Block 3: Misc / IVs
        ByteBuffer bE = ByteBuffer.wrap(blocks[3]).order(ByteOrder.LITTLE_ENDIAN);
        long newIvDword = (hpIv & 0x1F) | ((atkIv & 0x1F) << 5) | ((defIv & 0x1F) << 10) 
                        | ((speedIv & 0x1F) << 15) | ((spAtkIv & 0x1F) << 20) | ((spDefIv & 0x1F) << 25)
                        | ((long)isEgg << 30) | ((long)abilitySlot << 31);
        bE.putInt(4, (int)newIvDword);

        // Calculate Checksum of decrypted blocks
        int checksum = 0;
        for (byte[] block : blocks) {
            for (int i = 0; i < 12; i += 2) {
                checksum += (ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN).getShort(i) & 0xFFFF);
            }
        }
        bb.putShort(28, (short)(checksum & 0xFFFF));

        // Shuffle Blocks
        int order = (int)(pv % 24);
        byte[] decrypted = new byte[48];
        for (int i = 0; i < 4; i++) {
            int blockIndex = getBlockIndex(order, i);
            System.arraycopy(blocks[blockIndex], 0, decrypted, i * 12, 12);
        }

        // Encrypt (XOR with PV ^ OTID)
        long key = pv ^ otId;
        ByteBuffer db = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 48; i += 4) {
            bb.putInt(32 + i, db.getInt(i) ^ (int)key);
        }

        return output;
    }
}
