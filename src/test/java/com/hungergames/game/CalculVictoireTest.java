package com.hungergames.game;

import com.hungergames.game.CalculVictoire.Issue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculVictoireTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void uneEquipeResteEnVieTantQuUnMembreSurvit() {
        assertTrue(CalculVictoire.aUnSurvivant(List.of(a, b), Set.of(a)::contains));
        assertFalse(CalculVictoire.aUnSurvivant(List.of(a, b), Set.of(a, b)::contains));
    }

    @Test
    void lesEquipesEntierementElimineesNeSurviventPas() {
        List<List<UUID>> equipes = List.of(List.of(a, b), List.of(c), List.of(d));

        List<List<UUID>> survivantes = CalculVictoire.survivantes(equipes, Set.of(a, b, c)::contains);

        assertEquals(List.of(List.of(d)), survivantes);
    }

    @Test
    void laPartieContinueAuDelaDeDeuxEquipes() {
        assertEquals(Issue.EN_COURS, CalculVictoire.evaluer(4, 3));
    }

    @Test
    void deuxEquipesRestantesDeclenchentLaFinale() {
        assertEquals(Issue.FINALE, CalculVictoire.evaluer(4, 2));
    }

    @Test
    void uneSeuleEquipeRestanteGagne() {
        assertEquals(Issue.VICTOIRE, CalculVictoire.evaluer(4, 1));
    }

    @Test
    void aucunSurvivantDonneUneEgalite() {
        assertEquals(Issue.EGALITE, CalculVictoire.evaluer(2, 0));
    }

    @Test
    void unePartieDeTestAUneEquipeNeSeClotureJamaisSeule() {
        assertEquals(Issue.SANS_ADVERSAIRE, CalculVictoire.evaluer(1, 1));
        assertEquals(Issue.SANS_ADVERSAIRE, CalculVictoire.evaluer(1, 0));
    }
}
