package com.hungergames.equipe;

import com.hungergames.config.Messages;
import org.bukkit.ChatColor;

import java.util.UUID;

public class PropositionEquipe {
    private final UUID joueur1;
    private final UUID joueur2;

    private String nom = Messages.get("propositionequipe.equipe");
    private ChatColor couleur = ChatColor.WHITE;

    private boolean pretJoueur1 = false;
    private boolean pretJoueur2 = false;

    public PropositionEquipe(UUID joueur1, UUID joueur2) {
        this.joueur1 = joueur1;
        this.joueur2 = joueur2;
    }

    public UUID getJoueur1() {
        return joueur1;
    }

    public UUID getJoueur2() {
        return joueur2;
    }

    public UUID getAutre(UUID joueur) {
        return joueur.equals(joueur1) ? joueur2 : joueur1;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
        reinitialiserPret();
    }

    public ChatColor getCouleur() {
        return couleur;
    }

    public void setCouleur(ChatColor couleur) {
        this.couleur = couleur;
        reinitialiserPret();
    }

    private void reinitialiserPret() {
        pretJoueur1 = false;
        pretJoueur2 = false;
    }

    public boolean estPret(UUID joueur) {
        return joueur.equals(joueur1) ? pretJoueur1 : pretJoueur2;
    }

    public void setPret(UUID joueur, boolean pret) {
        if (joueur.equals(joueur1)) {
            pretJoueur1 = pret;
        } else {
            pretJoueur2 = pret;
        }
    }

    public boolean estFinalisable() {
        return pretJoueur1 && pretJoueur2;
    }
}
