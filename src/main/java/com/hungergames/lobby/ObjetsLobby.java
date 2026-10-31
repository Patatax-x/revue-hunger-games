package com.hungergames.lobby;

import com.hungergames.config.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ObjetsLobby {
    public static final int MODEL_DATA_EQUIPE = 9001;
    public static final int MODEL_DATA_CLASSE = 9002;
    public static final int MODEL_DATA_MODIFIER = 9003;
    public static final int SLOT_MODIFIER = 8;

    private ObjetsLobby() {
    }

    public static boolean possedeObjetEquipe(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && correspond(item, Material.LEAD, MODEL_DATA_EQUIPE)) return true;
        }
        return false;
    }

    public static boolean possedeObjetClasse(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && correspond(item, Material.BOOK, MODEL_DATA_CLASSE)) return true;
        }
        return false;
    }

    public static void retirerObjetsChoix(Player player) {
        retirerObjetEquipe(player);
        retirerObjetClasse(player);
    }

    public static void retirerObjetEquipe(Player player) {
        ItemStack[] contenu = player.getInventory().getContents();

        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];
            if (item != null && correspond(item, Material.LEAD, MODEL_DATA_EQUIPE)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public static void retirerObjetClasse(Player player) {
        ItemStack[] contenu = player.getInventory().getContents();

        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];
            if (item != null && correspond(item, Material.BOOK, MODEL_DATA_CLASSE)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public static boolean correspond(ItemStack item, Material material, int modelData) {
        if (item.getType() != material) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == modelData;
    }

    public static ItemStack creerItemEquipe() {
        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("lobbymanager.choisir_mon_equipe"));
            meta.setLore(List.of(Messages.get("lobbymanager.clic_droit_pour_ouvrir_le_menu")));
            meta.setCustomModelData(MODEL_DATA_EQUIPE);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack creerItemModifier() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("lobbymanager.modifier_2"));
            meta.setLore(List.of(Messages.get("lobbymanager.change_de_classe_ou_equipe"), Messages.get("lobbymanager.clic_droit_pour_ouvrir_le_menu")));
            meta.setCustomModelData(MODEL_DATA_MODIFIER);
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void donnerObjetModifier(Player player) {
        player.getInventory().setItem(SLOT_MODIFIER, creerItemModifier());
    }

    public static void retirerObjetModifier(Player player) {
        ItemStack[] contenu = player.getInventory().getContents();

        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];
            if (item != null && correspond(item, Material.BARRIER, MODEL_DATA_MODIFIER)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public static ItemStack creerItemClasse() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("lobbymanager.choisir_ma_classe"));
            meta.setLore(List.of(Messages.get("lobbymanager.clic_droit_pour_ouvrir_le_menu")));
            meta.setCustomModelData(MODEL_DATA_CLASSE);
            item.setItemMeta(meta);
        }
        return item;
    }
}
