package com.hungergames.loot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionObjetTest {
    @Test
    void lireUnObjetSimple() {
        DefinitionObjet objet = DefinitionObjet.parser("bread 4");

        assertEquals("BREAD", objet.materiau());
        assertEquals(4, objet.quantite());
        assertEquals(Map.of(), objet.enchantements());
        assertNull(objet.potion());
    }

    @Test
    void laQuantiteVautUnParDefaut() {
        assertEquals(1, DefinitionObjet.parser("SHIELD").quantite());
    }

    @Test
    void lireEnchantementsEtPotion() {
        assertEquals(Map.of("sharpness", 4, "fire_aspect", 1), DefinitionObjet.parser("DIAMOND_SWORD 1 sharpness:4 fire_aspect:1").enchantements());
        assertEquals("SWIFTNESS", DefinitionObjet.parser("POTION 1 potion:swiftness").potion());
    }

    @Test
    void refuserUneLigneInvalide() {
        assertThrows(IllegalArgumentException.class, () -> DefinitionObjet.parser("BREAD beaucoup"));
        assertThrows(IllegalArgumentException.class, () -> DefinitionObjet.parser("BREAD 0"));
        assertThrows(IllegalArgumentException.class, () -> DefinitionObjet.parser("BREAD 1 sharpness"));
        assertThrows(IllegalArgumentException.class, () -> DefinitionObjet.parser("   "));
    }
}
