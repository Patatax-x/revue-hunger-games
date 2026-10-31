package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandeEquipe implements CommandExecutor, TabCompleter {
    private final MenuEquipe menuEquipe;
    private final EquipeManager equipeManager;
    private final RoleManager roleManager;
    private final KillTracker killTracker;

    public CommandeEquipe(MenuEquipe menuEquipe, EquipeManager equipeManager, RoleManager roleManager, KillTracker killTracker) {
        this.menuEquipe = menuEquipe;
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
        this.killTracker = killTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
            return gererCreate(sender, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("add")) {
            return gererAdd(sender, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            return gererLeave(sender, args);
        }

        menuEquipe.ouvrirMenuBroadcast();
        sender.sendMessage(Messages.get("menuequipe.menu_de_choix_equipe_ouvert_pour"));
        return true;
    }

    private boolean commandesEquipeAutorisees(CommandSender sender) {
        boolean bloque = killTracker.estPartieDemarree();
        if (bloque) {
            sender.sendMessage(Messages.get("menuequipe.impossible_une_partie_est_en_cours"));
        }
        return !bloque;
    }

    private boolean gererCreate(CommandSender sender, String[] args) {
        if (!commandesEquipeAutorisees(sender)) {
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(Messages.get("menuequipe.usage_equipe_create_joueur_nom_couleur"));
            return true;
        }

        Player cible = Bukkit.getPlayerExact(args[1]);
        if (cible == null) {
            sender.sendMessage(Messages.get("commun.joueur_introuvable", "args", args[1]));
            return true;
        }

        if (roleManager.estExclu(cible)) {
            sender.sendMessage(Messages.get("menuequipe.ce_joueur_est_admin_afk_il"));
            return true;
        }

        ChatColor couleur = parseCouleur(args[3]);
        if (couleur == null) {
            sender.sendMessage(Messages.get("menuequipe.couleur_invalide_exemples_red_blue_green", "args", args[3]));
            return true;
        }

        boolean succes = equipeManager.creerEquipeSoloAvecNom(cible, args[2], couleur);
        if (!succes) {
            sender.sendMessage(Messages.get("menuequipe.impossible_de_creer_equipe_joueur_deja"));
            return true;
        }

        sender.sendMessage(Messages.get("menuequipe.equipe_creee_pour", "args", args[2], "cible", cible.getName()));
        return true;
    }

    private boolean gererAdd(CommandSender sender, String[] args) {
        if (!commandesEquipeAutorisees(sender)) {
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(Messages.get("menuequipe.usage_equipe_add_joueur_nom_equipe"));
            return true;
        }

        Player cible = Bukkit.getPlayerExact(args[1]);
        if (cible == null) {
            sender.sendMessage(Messages.get("commun.joueur_introuvable", "args", args[1]));
            return true;
        }

        if (roleManager.estExclu(cible)) {
            sender.sendMessage(Messages.get("menuequipe.ce_joueur_est_admin_afk_il"));
            return true;
        }

        EquipeManager.ResultatAjout resultat = equipeManager.ajouterAEquipeExistante(cible, args[2]);

        switch (resultat) {
            case SUCCES -> sender.sendMessage(Messages.get("menuequipe.ete_ajoute_equipe", "cible", cible.getName(), "args", args[2]));
            case JOUEUR_DEJA_EN_EQUIPE -> sender.sendMessage(Messages.get("menuequipe.ce_joueur_deja_une_equipe"));
            case EQUIPE_INTROUVABLE -> sender.sendMessage(Messages.get("menuequipe.equipe_introuvable", "args", args[2]));
            case EQUIPE_DEJA_COMPLETE -> sender.sendMessage(Messages.get("menuequipe.cette_equipe_deja_membres_duos_uniquement"));
        }

        return true;
    }

    private boolean gererLeave(CommandSender sender, String[] args) {
        if (!commandesEquipeAutorisees(sender)) {
            return true;
        }

        Player cible;
        if (args.length >= 2) {
            cible = Bukkit.getPlayerExact(args[1]);
            if (cible == null) {
                sender.sendMessage(Messages.get("commun.joueur_introuvable", "args", args[1]));
                return true;
            }
        } else if (sender instanceof Player joueur) {
            cible = joueur;
        } else {
            sender.sendMessage(Messages.get("menuequipe.precise_un_joueur_equipe_leave_joueur"));
            return true;
        }

        if (equipeManager.estDisponiblePourEquipe(cible.getUniqueId())) {
            sender.sendMessage(Messages.get("menuequipe.pas_equipe", "cible", cible.getName()));
            return true;
        }

        equipeManager.nettoyer(cible);
        if (equipeManager.aUneEquipe(cible)) {
            equipeManager.quitterEquipe(cible);
        }
        sender.sendMessage(Messages.get("menuequipe.equipe_quittee_pour", "cible", cible.getName()));
        return true;
    }

    private ChatColor parseCouleur(String texte) {
        try {
            ChatColor couleur = ChatColor.valueOf(texte.toUpperCase(Locale.ROOT));
            return couleur.isColor() ? couleur : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "add", "leave");
        }

        String sousCommande = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && (sousCommande.equals("create") || sousCommande.equals("add") || sousCommande.equals("leave"))) {
            return nomsJoueurs();
        }

        if (args.length == 3 && sousCommande.equals("add")) {
            return equipeManager.getNomsEquipesSolo();
        }

        if (args.length == 4 && sousCommande.equals("create")) {
            List<String> noms = new ArrayList<>();
            for (ChatColor couleur : MenuEquipe.couleursDisponibles()) {
                noms.add(couleur.name());
            }
            return noms;
        }

        return Collections.emptyList();
    }

    private List<String> nomsJoueurs() {
        List<String> noms = new ArrayList<>();
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            noms.add(joueur.getName());
        }
        return noms;
    }
}
