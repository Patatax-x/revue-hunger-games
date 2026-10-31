package com.hungergames.classe;

import com.hungergames.config.Messages;
import org.bukkit.Material;

public enum Classe {
    BOUCLIER(
            "classe_bouclier",
            Messages.get("classe.bouclier"),
            Material.SHIELD,
            Messages.get("classe.bouclier_passif_resistance_en_permanence_actif"),
            Messages.get("classe.resistance_permanente"),
            Messages.get("classe.repousse_tout_blocs_10_blocs_invincibilite")
    ),
    VITESSE(
            "classe_vitesse",
            Messages.get("classe.vitesse"),
            Material.FEATHER,
            Messages.get("classe.vitesse_passif_rapidite_en_permanence_actif"),
            Messages.get("classe.rapidite_permanente"),
            Messages.get("classe.dash_puissant_vers_avant")
    ),
    INVISIBLE(
            "classe_invisible",
            Messages.get("classe.invisible"),
            Material.SPYGLASS,
            Messages.get("classe.invisible_passif_invisible_tant_que_tu"),
            Messages.get("classe.invisible_tant_que_tu_ne_bouges"),
            Messages.get("classe.place_une_prime_min_sur_un")
    ),
    FEU(
            "classe_feu",
            Messages.get("classe.feu"),
            Material.BLAZE_ROD,
            Messages.get("classe.feu_passif_resistance_au_feu_en"),
            Messages.get("classe.resistance_au_feu_permanente"),
            Messages.get("classe.boule_de_feu_curs_de_degats")
    ),
    LOUP_GAROU(
            "classe_loup_garou",
            Messages.get("classe.loup_garou"),
            Material.BONE,
            Messages.get("classe.loup_garou_passif_cinq_curs_de"),
            Messages.get("classe.curs_de_vie_permanents"),
            Messages.get("classe.invoque_chiens_qui_attaquent_tout_seuls")
    );

    private final String tag;
    private final String displayName;
    private final Material itemActif;
    private final String pageLivre;
    private final String resumePassif;
    private final String resumeActif;

    Classe(String tag, String displayName, Material itemActif, String pageLivre, String resumePassif, String resumeActif) {
        this.tag = tag;
        this.displayName = displayName;
        this.itemActif = itemActif;
        this.pageLivre = pageLivre;
        this.resumePassif = resumePassif;
        this.resumeActif = resumeActif;
    }

    public String getTag() {
        return tag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getItemActif() {
        return itemActif;
    }

    public String getPageLivre() {
        return pageLivre;
    }

    public String getResumePassif() {
        return resumePassif;
    }

    public String getResumeActif() {
        return resumeActif;
    }

    public int getModelData() {
        return ordinal() + 1;
    }
}
