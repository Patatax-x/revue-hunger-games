package com.hungergames.loot;

import com.hungergames.config.Reglages;
import com.hungergames.map.GameMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigMapTest {
    private YamlConfiguration config;

    @BeforeEach
    void charger() {
        config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        Reglages.definir(config);
    }

    private GameMap map() {
        GameMap.charger(config.getConfigurationSection("maps"), "world", Logger.getLogger("test"));
        return GameMap.values().get(0);
    }

    @Test
    void lesValeursParDefautSontReprises() {
        ConfigMap configMap = new ConfigMap(map());

        assertEquals(115, configMap.getNombreCoffresTotal());
        assertEquals(25, configMap.getNombreCoffresCentre());
        assertEquals(90, configMap.getNombreCoffresEparpilles());
        assertEquals(2400, configMap.getDureeRetrecissementSecondes());
    }

    @Test
    void uneMapPeutRedefinirUneValeur() {
        config.set("maps.arena.chests.total", 10);

        assertEquals(10, new ConfigMap(map()).getNombreCoffresTotal());
    }

    @Test
    void uneModificationEstEcriteDansLaConfiguration() {
        ConfigMap configMap = new ConfigMap(map());

        configMap.setNombreCoffresTotal(42);
        configMap.setCentre(1, 2, 3);

        assertEquals(42, config.getInt("maps.arena.chests.total"));
        assertEquals(3.0, config.getDouble("maps.arena.chests.scatter-center.z"));
        assertEquals(42, new ConfigMap(map()).getNombreCoffresTotal());
    }

    @Test
    void unCoffreNormalNeTireJamaisLaRareteLargage() {
        config.set("map-defaults.rarity-weights.LARGAGE", 1000);
        ConfigMap configMap = new ConfigMap(map());
        Random random = new Random(1);

        for (int i = 0; i < 2000; i++) {
            assertNotEquals(Rarete.LARGAGE, configMap.tirerRarete(random));
        }
    }

    @Test
    void desPoidsNulsDonnentDuCommun() {
        for (Rarete rarete : Rarete.values()) {
            config.set("map-defaults.rarity-weights." + rarete.name(), 0);
        }

        assertEquals(Rarete.COMMUN, new ConfigMap(map()).tirerRarete(new Random(1)));
    }
}
