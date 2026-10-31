package com.hungergames.admin;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class Menus {
    private static final int LARGEUR = 9;
    private static final int TAILLE_MAX = 54;

    private Menus() {
    }

    public static int tailleMenu(int elements) {
        int rangees = Math.max(1, (int) Math.ceil(elements / (double) LARGEUR));
        return Math.min(TAILLE_MAX, rangees * LARGEUR);
    }

    /** Une seule rangée : centrée. Plusieurs : remplies dans l'ordre. Au-delà de 54, le surplus n'est pas affiché. */
    public static int[] emplacementsCentres(int elements) {
        int affiches = Math.min(elements, TAILLE_MAX);
        int decalage = elements < LARGEUR ? (LARGEUR - elements) / 2 : 0;
        int[] emplacements = new int[affiches];
        for (int i = 0; i < affiches; i++) {
            emplacements[i] = decalage + i;
        }
        return emplacements;
    }

    public static ItemStack creerItem(Material material, String nom, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(nom);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }
}
