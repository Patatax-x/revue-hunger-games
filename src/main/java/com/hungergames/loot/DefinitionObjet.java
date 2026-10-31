package com.hungergames.loot;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Une ligne de pool de config.yml : "DIAMOND_SWORD 1 sharpness:4" ou "POTION 1 potion:swiftness". */
public record DefinitionObjet(String materiau, int quantite, Map<String, Integer> enchantements, String potion) {
    public static DefinitionObjet parser(String ligne) {
        String[] parties = ligne.trim().split("\\s+");
        if (parties[0].isEmpty()) {
            throw new IllegalArgumentException("ligne vide");
        }

        int quantite = parties.length > 1 ? Integer.parseInt(parties[1]) : 1;
        if (quantite < 1) {
            throw new IllegalArgumentException("quantité invalide : " + ligne);
        }

        Map<String, Integer> enchantements = new LinkedHashMap<>();
        String potion = null;

        for (int i = 2; i < parties.length; i++) {
            String[] option = parties[i].split(":", 2);
            if (option.length != 2) {
                throw new IllegalArgumentException("option invalide : " + parties[i]);
            }

            String nom = option[0].toLowerCase(Locale.ROOT);
            if (nom.equals("potion")) {
                potion = option[1].toUpperCase(Locale.ROOT);
            } else {
                enchantements.put(nom, Integer.parseInt(option[1]));
            }
        }

        return new DefinitionObjet(parties[0].toUpperCase(Locale.ROOT), quantite, enchantements, potion);
    }
}
