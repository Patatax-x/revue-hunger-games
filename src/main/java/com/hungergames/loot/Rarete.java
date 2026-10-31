package com.hungergames.loot;

import com.hungergames.config.Messages;
import org.bukkit.Color;

public enum Rarete {
    COMMUN(Messages.get("rarete.commun"), Color.fromRGB(255, 255, 255)),
    PEU_COMMUN(Messages.get("rarete.peu_commun"), Color.fromRGB(85, 255, 85)),
    RARE(Messages.get("rarete.rare"), Color.fromRGB(85, 85, 255)),
    EPIQUE(Messages.get("rarete.epique"), Color.fromRGB(170, 0, 170)),
    LEGENDAIRE(Messages.get("rarete.legendaire"), Color.fromRGB(255, 170, 0)),

    LARGAGE(Messages.get("rarete.largage"), Color.fromRGB(0, 170, 0));

    private static final int VARIANTES_PAR_DEFAUT = 2;

    private static final int VARIANTES_LARGAGE = 4;

    private final String affichage;
    private final Color couleurParticule;

    Rarete(String affichage, Color couleurParticule) {
        this.affichage = affichage;
        this.couleurParticule = couleurParticule;
    }

    public int getNombreVariantes() {
        return this == LARGAGE ? VARIANTES_LARGAGE : VARIANTES_PAR_DEFAUT;
    }

    public String getAffichage() {
        return affichage;
    }

    public Color getCouleurParticule() {
        return couleurParticule;
    }
}
