package com.hungergames.loot;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class LootRegistry {
    private final File fichier;
    private final Logger logger;
    private final Map<String, List<ItemStack>> pools = new HashMap<>();
    private final Map<String, List<ItemStack>> poolsConfig = new HashMap<>();

    public LootRegistry(JavaPlugin plugin) {
        this.fichier = new File(plugin.getDataFolder(), "chest-items.yml");
        this.logger = plugin.getLogger();

        for (Rarete rarete : Rarete.values()) {
            for (int variante = 1; variante <= rarete.getNombreVariantes(); variante++) {
                pools.put(cle(rarete, variante), new ArrayList<>());
            }
        }

        charger();
    }

    private String cle(Rarete rarete, int variante) {
        return rarete.name() + "_" + variante;
    }

    public List<ItemStack> getPool(Rarete rarete, int variante) {
        List<ItemStack> capture = pools.getOrDefault(cle(rarete, variante), List.of());
        if (!capture.isEmpty()) {
            return capture;
        }
        return poolsConfig.computeIfAbsent(cle(rarete, variante), ignore -> LootTables.poolConfig(rarete, variante, logger));
    }

    public int taille(Rarete rarete, int variante) {
        return getPool(rarete, variante).size();
    }

    public void remplacer(Rarete rarete, int variante, List<ItemStack> items) {
        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack item : items) {
            pool.add(item.clone());
        }
        pools.put(cle(rarete, variante), pool);
        sauvegarder();
    }

    public int vider(Rarete rarete, int variante) {
        List<ItemStack> pool = pools.get(cle(rarete, variante));
        int n = pool == null ? 0 : pool.size();
        pools.put(cle(rarete, variante), new ArrayList<>());
        sauvegarder();
        return n;
    }

    public void charger() {
        for (List<ItemStack> pool : pools.values()) {
            pool.clear();
        }
        poolsConfig.clear();

        if (!fichier.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(fichier);

        for (Rarete rarete : Rarete.values()) {
            for (int variante = 1; variante <= rarete.getNombreVariantes(); variante++) {
                String cle = cle(rarete, variante);
                List<Map<?, ?>> entrees = yaml.getMapList(cle);
                List<ItemStack> pool = pools.computeIfAbsent(cle, ignore -> new ArrayList<>());

                for (Map<?, ?> entree : entrees) {
                    ItemStack item = deserialiser(entree);
                    if (item != null) {
                        pool.add(item);
                    }
                }
            }
        }
    }

    public void sauvegarder() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, List<ItemStack>> entree : pools.entrySet()) {
            List<Map<String, Object>> entrees = new ArrayList<>();
            for (ItemStack item : entree.getValue()) {
                entrees.add(serialiser(item));
            }
            yaml.set(entree.getKey(), entrees);
        }

        try {
            File dossierParent = fichier.getParentFile();
            if (dossierParent != null && !dossierParent.exists()) {
                dossierParent.mkdirs();
            }
            yaml.save(fichier);
        } catch (IOException exception) {
            logger.warning("Impossible d'écrire chest-items.yml : " + exception.getMessage());
        }
    }

    public void reinitialiserAuxDefauts() {
        for (List<ItemStack> pool : pools.values()) {
            pool.clear();
        }
        sauvegarder();
    }

    private Map<String, Object> serialiser(ItemStack item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("material", item.getType().getKey().toString());
        data.put("count", item.getAmount());
        data.put("nbt", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return data;
    }

    private ItemStack deserialiser(Map<?, ?> data) {
        Object nbt = data.get("nbt");
        if (nbt instanceof String base64 && !base64.isBlank()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Object materialRaw = data.get("material");
        Object countRaw = data.get("count");
        if (!(materialRaw instanceof String materialKey)) {
            return null;
        }

        Material material = Material.matchMaterial(materialKey);
        if (material == null || material.isAir()) {
            return null;
        }

        int count = countRaw instanceof Number nombre ? Math.max(1, nombre.intValue()) : 1;
        return new ItemStack(material, count);
    }
}
