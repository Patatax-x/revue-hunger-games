package com.hungergames.loot;

import com.hungergames.config.Reglages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class LootTables {
    private static final Random RANDOM = new Random();

    private static final Set<Material> uniquesDistribues = new HashSet<>();

    public static void reinitialiserPourNouvellePartie() {
        uniquesDistribues.clear();
    }

    public static List<ItemStack> poolConfig(Rarete rarete, int variante, Logger logger) {
        List<ItemStack> pool = new ArrayList<>();
        for (String ligne : Reglages.liste("loot.pools." + rarete.name() + "." + variante)) {
            try {
                pool.add(creerObjet(DefinitionObjet.parser(ligne)));
            } catch (IllegalArgumentException exception) {
                logger.warning("Objet ignoré dans loot.pools." + rarete.name() + "." + variante + " : " + exception.getMessage());
            }
        }
        return pool;
    }

    private static ItemStack creerObjet(DefinitionObjet definition) {
        Material material = Material.matchMaterial(definition.materiau());
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("matériau inconnu : " + definition.materiau());
        }

        ItemStack item = new ItemStack(material, definition.quantite());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            for (Map.Entry<String, Integer> entree : definition.enchantements().entrySet()) {
                Enchantment enchantement = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entree.getKey()));
                if (enchantement == null) {
                    throw new IllegalArgumentException("enchantement inconnu : " + entree.getKey());
                }
                meta.addEnchant(enchantement, entree.getValue(), true);
            }

            if (definition.potion() != null && meta instanceof PotionMeta potionMeta) {
                potionMeta.setBasePotionType(PotionType.valueOf(definition.potion()));
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static List<ItemStack> genererContenu(Rarete rarete, int variante, LootRegistry registry) {
        List<ItemStack> pool = registry.getPool(rarete, variante);
        List<ItemStack> contenu = new ArrayList<>();
        if (pool.isEmpty()) {
            return contenu;
        }

        int min = Reglages.entier("loot.items-per-chest.min");
        int max = Math.max(min, Reglages.entier("loot.items-per-chest.max"));
        int nombreItems = min + RANDOM.nextInt(max - min + 1);

        Set<Material> uniques = materiauxUniques();
        Set<Material> equipementDejaTire = new HashSet<>();

        for (int i = 0; i < nombreItems; i++) {
            ItemStack modele = tirerItem(pool, equipementDejaTire, uniques);
            if (modele == null) continue;

            if (estEquipement(modele.getType())) {
                equipementDejaTire.add(modele.getType());
            }
            if (uniques.contains(modele.getType())) {
                uniquesDistribues.add(modele.getType());
            }

            contenu.add(modele.clone());
        }

        return contenu;
    }

    private static Set<Material> materiauxUniques() {
        Set<Material> uniques = new HashSet<>();
        for (String nom : Reglages.liste("loot.unique-per-game")) {
            Material material = Material.matchMaterial(nom);
            if (material != null) {
                uniques.add(material);
            }
        }
        return uniques;
    }

    private static ItemStack tirerItem(List<ItemStack> pool, Set<Material> equipementDejaTire, Set<Material> uniques) {
        for (int tentative = 0; tentative < 10; tentative++) {
            ItemStack candidat = pool.get(RANDOM.nextInt(pool.size()));
            Material type = candidat.getType();

            if (estEquipement(type) && equipementDejaTire.contains(type)) continue;
            if (uniques.contains(type) && uniquesDistribues.contains(type)) continue;

            return candidat;
        }
        return null;
    }

    private static boolean estEquipement(Material material) {
        String nom = material.name();
        return nom.endsWith("_SWORD") || nom.endsWith("_AXE") || nom.endsWith("_HELMET")
                || nom.endsWith("_CHESTPLATE") || nom.endsWith("_LEGGINGS") || nom.endsWith("_BOOTS")
                || nom.endsWith("_PICKAXE") || nom.endsWith("_SHOVEL") || nom.endsWith("_HOE")
                || material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT
                || material == Material.MACE || material == Material.SHIELD || material == Material.TOTEM_OF_UNDYING;
    }
}
