package com.hungergames.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class CalculVictoire {
    public enum Issue { EN_COURS, FINALE, VICTOIRE, EGALITE, SANS_ADVERSAIRE }

    private CalculVictoire() {
    }

    public static List<List<UUID>> survivantes(List<List<UUID>> equipes, Predicate<UUID> estElimine) {
        List<List<UUID>> survivantes = new ArrayList<>();
        for (List<UUID> equipe : equipes) {
            if (aUnSurvivant(equipe, estElimine)) {
                survivantes.add(equipe);
            }
        }
        return survivantes;
    }

    public static boolean aUnSurvivant(List<UUID> equipe, Predicate<UUID> estElimine) {
        for (UUID uuid : equipe) {
            if (!estElimine.test(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** Avec une seule équipe au départ (partie de test), pas de clôture automatique. */
    public static Issue evaluer(int equipesAuDepart, int equipesSurvivantes) {
        if (equipesSurvivantes > 2) {
            return Issue.EN_COURS;
        }
        if (equipesSurvivantes == 2) {
            return Issue.FINALE;
        }
        if (equipesAuDepart < 2) {
            return Issue.SANS_ADVERSAIRE;
        }
        return equipesSurvivantes == 1 ? Issue.VICTOIRE : Issue.EGALITE;
    }
}
