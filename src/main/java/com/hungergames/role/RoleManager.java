package com.hungergames.role;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class RoleManager {
    private static final String EQUIPE_ADMIN = "role_admin_tag";
    private static final String EQUIPE_AFK = "role_afk_tag";

    private final Set<UUID> admins = new HashSet<>();
    private final Set<UUID> afk = new HashSet<>();

    private final List<Consumer<Player>> ecouteursAdminAssigne = new ArrayList<>();
    private final List<Consumer<Player>> ecouteursAdminRetire = new ArrayList<>();
    private final List<Consumer<Player>> ecouteursAfkAssigne = new ArrayList<>();
    private final List<Consumer<Player>> ecouteursAfkRetire = new ArrayList<>();

    public void ajouterEcouteurAdminAssigne(Consumer<Player> ecouteur) {
        ecouteursAdminAssigne.add(ecouteur);
    }

    public void ajouterEcouteurAdminRetire(Consumer<Player> ecouteur) {
        ecouteursAdminRetire.add(ecouteur);
    }

    public void ajouterEcouteurAfkAssigne(Consumer<Player> ecouteur) {
        ecouteursAfkAssigne.add(ecouteur);
    }

    public void ajouterEcouteurAfkRetire(Consumer<Player> ecouteur) {
        ecouteursAfkRetire.add(ecouteur);
    }

    public boolean estAdmin(Player player) {
        return admins.contains(player.getUniqueId());
    }

    public boolean estAfk(Player player) {
        return afk.contains(player.getUniqueId());
    }

    public boolean estExclu(Player player) {
        return estAdmin(player) || estAfk(player);
    }

    public void assignerAdmin(Player player) {
        if (estAfk(player)) {
            retirerAfk(player);
        }

        admins.add(player.getUniqueId());
        obtenirEquipeAdmin().addEntry(player.getName());
        player.sendMessage(Messages.get("rolemanager.tu_as_maintenant_le_role_admin"));

        for (Consumer<Player> ecouteur : ecouteursAdminAssigne) {
            ecouteur.accept(player);
        }
    }

    public void retirerAdmin(Player player) {
        if (!admins.remove(player.getUniqueId())) {
            return;
        }

        obtenirEquipeAdmin().removeEntry(player.getName());
        player.sendMessage(Messages.get("rolemanager.le_role_admin_ete_retire"));

        for (Consumer<Player> ecouteur : ecouteursAdminRetire) {
            ecouteur.accept(player);
        }
    }

    public void assignerAfk(Player player) {
        if (estAdmin(player)) {
            retirerAdmin(player);
        }

        afk.add(player.getUniqueId());
        obtenirEquipeAfk().addEntry(player.getName());
        player.sendMessage(Messages.get("rolemanager.tu_as_maintenant_le_role_afk"));

        for (Consumer<Player> ecouteur : ecouteursAfkAssigne) {
            ecouteur.accept(player);
        }
    }

    public void retirerAfk(Player player) {
        if (!afk.remove(player.getUniqueId())) {
            return;
        }

        obtenirEquipeAfk().removeEntry(player.getName());
        player.sendMessage(Messages.get("rolemanager.le_role_afk_ete_retire"));

        for (Consumer<Player> ecouteur : ecouteursAfkRetire) {
            ecouteur.accept(player);
        }
    }

    public void retirerTousLesRoles(Player player) {
        retirerAdmin(player);
        retirerAfk(player);
    }

    private Team obtenirEquipeAdmin() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(EQUIPE_ADMIN);

        if (team == null) {
            team = scoreboard.registerNewTeam(EQUIPE_ADMIN);
            team.setColor(ChatColor.DARK_RED);
            team.setPrefix(ChatColor.DARK_RED + "" + ChatColor.BOLD + "♛ " + ChatColor.RESET);
        }

        return team;
    }

    private Team obtenirEquipeAfk() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(EQUIPE_AFK);

        if (team == null) {
            team = scoreboard.registerNewTeam(EQUIPE_AFK);
            team.setColor(ChatColor.GRAY);
            team.setPrefix(ChatColor.GRAY + "" + ChatColor.BOLD + "💤 " + ChatColor.RESET);
        }

        return team;
    }
}
