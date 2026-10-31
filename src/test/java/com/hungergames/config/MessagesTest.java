package com.hungergames.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {
    @Test
    void remplacerLesValeursEtLesCouleurs() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("a", "&c{joueur} a {kills} kills");
        Messages.definir(yaml);

        assertEquals("§cAlex a 3 kills", Messages.get("a", "joueur", "Alex", "kills", 3));
    }

    @Test
    void uneValeurNePeutPasInjecterDeCouleur() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("a", "{nom}");
        Messages.definir(yaml);

        assertEquals("&cRouge", Messages.get("a", "nom", "&cRouge"));
    }

    @Test
    void uneCleAbsenteRenvoieLaCle() {
        Messages.definir(new YamlConfiguration());

        assertEquals("absente.cle", Messages.get("absente.cle"));
    }
}
