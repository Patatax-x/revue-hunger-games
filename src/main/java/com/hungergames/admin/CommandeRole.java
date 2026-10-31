package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.game.KillTracker;
import com.hungergames.lobby.LobbyManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommandeRole implements CommandExecutor, TabCompleter {
    private final RoleManager roleManager;
    private final LobbyManager lobbyManager;
    private final KillTracker killTracker;

    public CommandeRole(RoleManager roleManager, LobbyManager lobbyManager, KillTracker killTracker) {
        this.roleManager = roleManager;
        this.lobbyManager = lobbyManager;
        this.killTracker = killTracker;
    }

    private boolean retraitAdminAutorise() {
        LobbyManager.EtatPartie etat = lobbyManager.getEtat();
        boolean etatAutorise = etat == LobbyManager.EtatPartie.OFF || etat == LobbyManager.EtatPartie.LOBBY;
        return etatAutorise && !killTracker.estPartieDemarree();
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("liste"))) {
            return gererListe(sender, args);
        }

        if (args.length != 3) {
            sender.sendMessage(Messages.get("commanderole.usage_role_add_remove_admin_afk"));
            sender.sendMessage(Messages.get("commanderole.usage_role_list_joueur"));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String role = args[1].toLowerCase(Locale.ROOT);

        boolean ajouter;
        if (action.equals("add") || action.equals("ajouter")) {
            ajouter = true;
        } else if (action.equals("remove") || action.equals("retirer")) {
            ajouter = false;
        } else {
            sender.sendMessage(Messages.get("commanderole.action_inconnue_add_remove", "action", action));
            return true;
        }

        List<Player> cibles = resoudreCibles(args[2]);

        if (cibles.isEmpty()) {
            sender.sendMessage(Messages.get("commanderole.aucun_joueur_trouve_pour", "args", args[2]));
            return true;
        }

        switch (role) {
            case "admin" -> {
                if (!ajouter && !retraitAdminAutorise()) {
                    sender.sendMessage(Messages.get("commanderole.le_role_admin_ne_peut_etre"));
                    return true;
                }
                for (Player cible : cibles) {
                    if (ajouter) roleManager.assignerAdmin(cible);
                    else roleManager.retirerAdmin(cible);
                }
            }
            case "afk" -> {
                for (Player cible : cibles) {
                    if (ajouter) roleManager.assignerAfk(cible);
                    else roleManager.retirerAfk(cible);
                }
            }
            case "tous", "all" -> {
                if (ajouter) {
                    sender.sendMessage(Messages.get("commanderole.impossible_admin_et_afk_sont_mutuellement"));
                    sender.sendMessage(Messages.get("commanderole.utilise_role_add_admin_afk_joueur"));
                    return true;
                }

                for (Player cible : cibles) {
                    roleManager.retirerTousLesRoles(cible);
                }
            }
            default -> {
                sender.sendMessage(Messages.get("commanderole.role_inconnu_admin_afk_tous", "role", role));
                return true;
            }
        }

        sender.sendMessage(Messages.get("commanderole.role_joueur", "role", role, "ajouter", (ajouter ? Messages.get("commanderole.attribue") : Messages.get("commanderole.retire")), "cibles", cibles.size()));

        return true;
    }

    private boolean gererListe(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Messages.get("commanderole.usage_role_list_joueur"));
            return true;
        }

        List<Player> cibles = resoudreCibles(args[1]);

        if (cibles.isEmpty()) {
            sender.sendMessage(Messages.get("commanderole.aucun_joueur_trouve_pour", "args", args[1]));
            return true;
        }

        for (Player cible : cibles) {
            List<String> roles = new ArrayList<>();
            if (roleManager.estAdmin(cible)) roles.add("admin");
            if (roleManager.estAfk(cible)) roles.add("afk");

            String texte = roles.isEmpty() ? Messages.get("commanderole.aucun_role") : Messages.get("commanderole.roles", "roles", String.join(Messages.get("commun.separateur_liste"), roles));
            sender.sendMessage(Messages.get("commanderole.cible_texte", "cible", cible.getName(), "texte", texte));
        }

        return true;
    }

    private List<Player> resoudreCibles(String argument) {
        if (argument.equalsIgnoreCase("@a")) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        Player joueur = Bukkit.getPlayerExact(argument);

        if (joueur == null) {
            return List.of();
        }

        return List.of(joueur);
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
        if (args.length == 1) {
            return List.of("add", "remove", "list");
        }

        boolean estListe = args.length > 0 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("liste"));

        if (args.length == 2 && estListe) {
            return nomsJoueurs();
        }

        if (args.length == 2) {
            return List.of("admin", "afk", "tous");
        }

        if (args.length == 3) {
            return nomsJoueurs();
        }

        return List.of();
    }

    private List<String> nomsJoueurs() {
        List<String> noms = new ArrayList<>();
        noms.add("@a");
        for (Player joueur : Bukkit.getOnlinePlayers()) {
            noms.add(joueur.getName());
        }
        return noms;
    }
}
