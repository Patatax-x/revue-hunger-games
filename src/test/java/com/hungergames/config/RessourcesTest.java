package com.hungergames.config;

import com.hungergames.loot.DefinitionObjet;
import com.hungergames.loot.Rarete;
import com.hungergames.map.GameMap;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie que le code, plugin.yml, config.yml et messages.yml restent alignés. */
class RessourcesTest {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path RESSOURCES = Path.of("src/main/resources");

    private static String sourcesConcatenees() throws IOException {
        StringBuilder texte = new StringBuilder();
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            for (Path fichier : (Iterable<Path>) fichiers.filter(f -> f.toString().endsWith(".java"))::iterator) {
                texte.append(Files.readString(fichier, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return texte.toString();
    }

    private static YamlConfiguration yaml(String nom) {
        return YamlConfiguration.loadConfiguration(RESSOURCES.resolve(nom).toFile());
    }

    private static List<String> captures(String texte, String regex) {
        List<String> trouve = new ArrayList<>();
        Matcher matcher = Pattern.compile(regex).matcher(texte);
        while (matcher.find()) {
            trouve.add(matcher.group(1));
        }
        return trouve;
    }

    @Test
    void chaqueMessageUtiliseExiste() throws IOException {
        YamlConfiguration messages = yaml("messages.yml");
        List<String> absents = new ArrayList<>();

        for (String cle : captures(sourcesConcatenees(), "Messages\\.get\\(\"([^\"]+)\"")) {
            if (!messages.isString(cle)) {
                absents.add(cle);
            }
        }

        assertEquals(List.of(), absents);
    }

    @Test
    void chaqueMessageDeclareEstUtilise() throws IOException {
        Set<String> utilises = new HashSet<>(captures(sourcesConcatenees(), "Messages\\.get\\(\"([^\"]+)\""));
        List<String> orphelins = new ArrayList<>();

        for (String cle : yaml("messages.yml").getKeys(true)) {
            if (yaml("messages.yml").isString(cle) && !utilises.contains(cle)) {
                orphelins.add(cle);
            }
        }

        assertEquals(List.of(), orphelins);
    }

    @Test
    void lesValeursDynamiquesCorrespondentAuTexte() throws IOException {
        YamlConfiguration messages = yaml("messages.yml");
        List<String> incoherences = new ArrayList<>();
        Matcher appel = Pattern.compile("Messages\\.get\\(\"([^\"]+)\"(?:,\\s*\"([a-z0-9_]+)\",\\s*)?").matcher(sourcesConcatenees());

        while (appel.find()) {
            String texte = messages.getString(appel.group(1), "");
            boolean correct = appel.group(2) == null ? !texte.contains("{") : texte.contains("{" + appel.group(2) + "}");
            if (!correct) {
                incoherences.add(appel.group(1));
            }
        }

        assertEquals(List.of(), incoherences);
    }

    @Test
    void chaqueReglageUtiliseExiste() throws IOException {
        YamlConfiguration config = yaml("config.yml");
        List<String> absents = new ArrayList<>();

        for (String chemin : captures(sourcesConcatenees(), "Reglages\\.(?:entier|decimal|booleen|texte|liste)\\(\"([^\"]+)\"\\)")) {
            if (!config.contains(chemin)) {
                absents.add(chemin);
            }
        }

        assertEquals(List.of(), absents);
    }

    @Test
    void chaqueCommandeDuCodeEstDeclareeEtInversement() throws IOException {
        Set<String> dansLeCode = new HashSet<>(captures(sourcesConcatenees(), "getCommand\\(\"([^\"]+)\"\\)"));
        YamlConfiguration plugin = yaml("plugin.yml");
        Set<String> declarees = plugin.getConfigurationSection("commands").getKeys(false);

        assertEquals(declarees, dansLeCode);

        for (String commande : declarees) {
            String permission = plugin.getString("commands." + commande + ".permission");
            assertNotNull(permission, commande);
            assertTrue(plugin.contains("permissions." + permission), permission);
        }
    }

    @Test
    void chaqueTitreDeMenuEstUnique() throws IOException {
        YamlConfiguration messages = yaml("messages.yml");
        Set<String> vus = new HashSet<>();

        for (String cle : captures(sourcesConcatenees(), "TITRE_\\w+ = Messages\\.get\\(\"([^\"]+)\"\\)")) {
            assertTrue(vus.add(messages.getString(cle)), "titre en double : " + cle);
        }
        assertFalse(vus.isEmpty());
    }

    @Test
    void chaquePoolDeLootEstRempliEtValide() {
        YamlConfiguration config = yaml("config.yml");

        for (Rarete rarete : Rarete.values()) {
            for (int variante = 1; variante <= rarete.getNombreVariantes(); variante++) {
                List<String> pool = config.getStringList("loot.pools." + rarete.name() + "." + variante);
                assertFalse(pool.isEmpty(), rarete + " variante " + variante);

                for (String ligne : pool) {
                    DefinitionObjet objet = DefinitionObjet.parser(ligne);
                    assertNotNull(Material.matchMaterial(objet.materiau()), ligne);
                }
            }
        }
    }

    @Test
    void lesMapsDeLaConfigurationParDefautSeChargent() {
        YamlConfiguration config = yaml("config.yml");

        GameMap.charger(config.getConfigurationSection("maps"), "world", Logger.getLogger("test"));

        assertFalse(GameMap.values().isEmpty());
        for (GameMap map : GameMap.values()) {
            assertTrue(map.getRayonBordure() > map.getRayon(), map.getCle());
        }
    }

    @Test
    void uneMapDangereusePourResetmapEstRefusee() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("maps.world.spawn-radius", 10);
        config.set("maps.valide.backup-folder", "../../ailleurs");
        config.set("maps.correcte.backup-folder", "correcte_backup");

        GameMap.charger(config.getConfigurationSection("maps"), "world", Logger.getLogger("test"));

        assertEquals(1, GameMap.values().size());
        assertEquals("correcte", GameMap.values().get(0).getCle());
    }
}
